package com.eternamente.app.domain.ml

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * On-device Isolation Forest for personalised cognitive-change detection.
 *
 * Builds an ensemble of [NUM_TREES] binary trees that partition the feature space
 * using random feature + random split-value at each node. Points that can be
 * isolated with fewer splits score closer to 1.0 (anomalous); points that require
 * deep paths score closer to 0.5 (normal).
 *
 * **Lifecycle:**
 * 1. On first inject, [load] restores a previously trained forest from internal storage.
 * 2. [CognitiveAnalyzer] calls [addAndRetrain] after each analysis to accumulate history.
 * 3. Once [MIN_SAMPLES] vectors are available, the forest is rebuilt and persisted.
 *
 * Returns 0.5f (neutral) whenever the model is untrained (< [MIN_SAMPLES] history points).
 */
@Singleton
class IsolationForestModel @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── Configuration ─────────────────────────────────────────────────────────

    companion object {
        /** Minimum feature vectors required before anomaly scores are meaningful. */
        const val MIN_SAMPLES = 10

        private const val NUM_TREES       = 100
        private const val SUBSAMPLE_SIZE  = 64
        private const val MAX_HISTORY     = 200
        private const val MODEL_FILE_NAME = "isolation_forest.json"
        private const val EULER_GAMMA     = 0.5772156649
        private const val TAG             = "IsolationForest"
    }

    // ── Internal data model ───────────────────────────────────────────────────

    private data class Node(
        val splitFeature: Int = -1,   // < 0 → leaf
        val splitValue:   Float = 0f,
        val size:         Int   = 1,
        val left:  Node? = null,
        val right: Node? = null
    ) {
        val isLeaf: Boolean get() = splitFeature < 0
    }

    // ── Mutable state (guarded by `this`) ─────────────────────────────────────

    private var forest:            List<Node>            = emptyList()
    private var trainSize:         Int                   = 0
    private var historicalVectors: MutableList<FloatArray> = mutableListOf()
    @Volatile private var initialized = false

    init { load() }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Trains from scratch using [data]. Replaces any previously loaded model.
     * No-ops if [data].size < [MIN_SAMPLES].
     */
    @Synchronized
    fun fit(data: List<FeatureVector>) {
        if (data.size < MIN_SAMPLES) return
        historicalVectors = data
            .map { it.normalized ?: FeatureNormalizer.normalize(it).normalized!! }
            .takeLast(MAX_HISTORY)
            .toMutableList()
        rebuildForest()
        save()
    }

    /**
     * Appends [vector] to the user's history and retrains if sufficient data exists.
     *
     * @return `true` if the forest was successfully trained; `false` if still accumulating.
     */
    @Synchronized
    fun addAndRetrain(vector: FeatureVector): Boolean {
        val norm = vector.normalized ?: FeatureNormalizer.normalize(vector).normalized!!
        if (historicalVectors.size >= MAX_HISTORY) historicalVectors.removeAt(0)
        historicalVectors.add(norm)
        if (historicalVectors.size < MIN_SAMPLES) return false
        rebuildForest()
        save()
        return true
    }

    /**
     * Returns the isolation-based anomaly score for [point] in [0, 1].
     * Values significantly above 0.5 indicate the point is unusual relative to the user's history.
     * Returns 0.5 (neutral) when the model has not been trained yet.
     */
    @Synchronized
    fun anomalyScore(point: FeatureVector): Float {
        if (forest.isEmpty() || historicalVectors.size < MIN_SAMPLES) return 0.5f
        val features = point.normalized ?: FeatureNormalizer.normalize(point).normalized!!
        val avgPath = forest.map { root -> pathLength(features, root, 0) }.average().toFloat()
        val cn = c(trainSize)
        if (cn == 0f) return 0.5f
        return 2.0.pow((-avgPath / cn).toDouble()).toFloat().coerceIn(0f, 1f)
    }

    val isTrained:     Boolean get() = forest.isNotEmpty() && historicalVectors.size >= MIN_SAMPLES
    val historySizeSnapshot: Int get() = historicalVectors.size

    // ── Core algorithm ────────────────────────────────────────────────────────

    private fun rebuildForest() {
        val n = minOf(SUBSAMPLE_SIZE, historicalVectors.size)
        trainSize = n
        val maxDepth = ceil(ln(n.toDouble()) / ln(2.0)).toInt().coerceAtLeast(1)
        val rng = Random(System.currentTimeMillis())

        forest = List(NUM_TREES) {
            val subsample = historicalVectors.shuffled(rng).take(n)
            buildTree(subsample, 0, maxDepth, rng)
        }
        Timber.d("$TAG: rebuilt — $NUM_TREES trees, n=$n, maxDepth=$maxDepth")
    }

    private fun buildTree(
        data:     List<FloatArray>,
        depth:    Int,
        maxDepth: Int,
        rng:      Random
    ): Node {
        if (data.size <= 1 || depth >= maxDepth) return Node(size = data.size)

        val featureIdx = rng.nextInt(FeatureVector.FEATURE_COUNT)
        val colMin = data.minOf { it[featureIdx] }
        val colMax = data.maxOf { it[featureIdx] }

        if (colMin >= colMax) return Node(size = data.size)

        val split = colMin + rng.nextFloat() * (colMax - colMin)
        val left  = data.filter { it[featureIdx] < split }
        val right = data.filter { it[featureIdx] >= split }

        return Node(
            splitFeature = featureIdx,
            splitValue   = split,
            size         = data.size,
            left         = buildTree(left,  depth + 1, maxDepth, rng),
            right        = buildTree(right, depth + 1, maxDepth, rng)
        )
    }

    private fun pathLength(point: FloatArray, node: Node, depth: Int): Float {
        if (node.isLeaf) return depth.toFloat() + c(node.size)
        return if (point[node.splitFeature] < node.splitValue)
            pathLength(point, node.left!!, depth + 1)
        else
            pathLength(point, node.right!!, depth + 1)
    }

    /**
     * Expected path length adjustment for a BST of [n] elements.
     * Formula from Liu et al. (2008): c(n) = 2·H(n−1) − 2·(n−1)/n
     * where H(k) ≈ ln(k) + γ (Euler–Mascheroni constant).
     */
    private fun c(n: Int): Float = when {
        n <= 1 -> 0f
        n == 2 -> 1f
        else   -> (2.0 * (ln(n - 1.0) + EULER_GAMMA) - 2.0 * (n - 1.0) / n).toFloat()
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private fun save() {
        try {
            val dir  = File(context.filesDir, "ml").also { it.mkdirs() }
            val file = File(dir, MODEL_FILE_NAME)

            val root = JSONObject()
            root.put("trainSize", trainSize)

            val histArr = JSONArray()
            historicalVectors.forEach { vec ->
                val row = JSONArray()
                vec.forEach { v -> row.put(v.toDouble()) }
                histArr.put(row)
            }
            root.put("history", histArr)

            val forestArr = JSONArray()
            forest.forEach { node -> forestArr.put(nodeToJson(node)) }
            root.put("forest", forestArr)

            file.writeText(root.toString())
            Timber.d("$TAG: saved ${file.length()} bytes, ${historicalVectors.size} history vectors")
        } catch (e: Exception) {
            Timber.e("$TAG: save failed — ${e.message}")
        }
    }

    private fun load() {
        try {
            val file = File(context.filesDir, "ml/$MODEL_FILE_NAME")
            if (!file.exists()) {
                Timber.d("$TAG: no saved model, starting fresh")
                return
            }
            val root = JSONObject(file.readText())
            trainSize = root.getInt("trainSize")

            val histArr = root.getJSONArray("history")
            historicalVectors = mutableListOf()
            repeat(histArr.length()) { i ->
                val row = histArr.getJSONArray(i)
                historicalVectors.add(FloatArray(row.length()) { j -> row.getDouble(j).toFloat() })
            }

            val forestArr = root.getJSONArray("forest")
            forest = (0 until forestArr.length()).map { i ->
                nodeFromJson(forestArr.getJSONObject(i))
            }
            Timber.d("$TAG: loaded — ${forest.size} trees, ${historicalVectors.size} history vectors")
        } catch (e: Exception) {
            Timber.w("$TAG: load failed, starting fresh — ${e.message}")
            forest = emptyList()
            historicalVectors = mutableListOf()
        }
    }

    // ── JSON helpers (short keys for compact output) ───────────────────────────

    private fun nodeToJson(node: Node): JSONObject {
        val obj = JSONObject()
        obj.put("l", node.isLeaf)
        obj.put("s", node.size)
        if (!node.isLeaf) {
            obj.put("f", node.splitFeature)
            obj.put("v", node.splitValue.toDouble())
            obj.put("L", nodeToJson(node.left!!))
            obj.put("R", nodeToJson(node.right!!))
        }
        return obj
    }

    private fun nodeFromJson(obj: JSONObject): Node {
        val isLeaf = obj.getBoolean("l")
        val size   = obj.getInt("s")
        if (isLeaf) return Node(size = size)
        return Node(
            splitFeature = obj.getInt("f"),
            splitValue   = obj.getDouble("v").toFloat(),
            size         = size,
            left         = nodeFromJson(obj.getJSONObject("L")),
            right        = nodeFromJson(obj.getJSONObject("R"))
        )
    }
}
