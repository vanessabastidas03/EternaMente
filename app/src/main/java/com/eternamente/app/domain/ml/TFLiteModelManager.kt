package com.eternamente.app.domain.ml

import android.content.Context
import com.eternamente.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the on-device TFLite cognitive-risk model.
 *
 * Loads `res/raw/eternamente_ml_v1.tflite` lazily on first use. If the model file
 * is absent (e.g. during development) or fails to load, inference falls back to a
 * weighted statistical rule set that produces equivalent coarse-grained scores.
 *
 * Inference always runs on [Dispatchers.Default] to keep the UI responsive.
 *
 * **Error handling:**
 * - [Resources.NotFoundException] / resource ID 0 → fallback, logs warning
 * - [OutOfMemoryError] during load or inference → fallback, logs error
 * - [IllegalStateException] during inference → fallback, logs error
 *
 * Model version is exposed via [BuildConfig.ML_MODEL_VERSION] so telemetry can
 * track which model produced a given prediction.
 */
@Singleton
class TFLiteModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG                = "TFLiteModelManager"
        private const val MODEL_RESOURCE_NAME = "eternamente_ml_v1"
        private const val INPUT_SIZE          = FeatureVector.FEATURE_COUNT  // 14
        private const val OUTPUT_SIZE         = 1                            // single risk score
    }

    // Lazy: initialised on first call to runInference / hasModel.
    private val interpreter: Interpreter? by lazy { tryLoadInterpreter() }

    val hasModel: Boolean get() = interpreter != null
    val modelVersion: String get() =
        if (hasModel) BuildConfig.ML_MODEL_VERSION else "statistical_fallback"

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs the TFLite model on the given (already normalised) [features] array and
     * returns the raw output tensor. Falls back to [statisticalFallback] if the model
     * is unavailable or throws.
     *
     * @param features Normalised 14-element float array from [FeatureNormalizer].
     * @return FloatArray of length 1 containing the risk score in [0, 1].
     */
    suspend fun runInference(features: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        val interp = interpreter ?: return@withContext statisticalFallback(features)

        val startMs = System.currentTimeMillis()
        try {
            val input  = Array(1) { features.copyOf(INPUT_SIZE) }
            val output = Array(1) { FloatArray(OUTPUT_SIZE) }
            interp.run(input, output)
            val elapsedMs = System.currentTimeMillis() - startMs
            Timber.v("$TAG: inference OK — score=${output[0][0]}, elapsed=${elapsedMs}ms")
            if (elapsedMs > 500) Timber.w("$TAG: inference lenta (${elapsedMs}ms > 500ms umbral)")
            output[0]
        } catch (e: IllegalStateException) {
            Timber.e("$TAG: inference error — ${e.message}")
            statisticalFallback(features)
        } catch (oom: OutOfMemoryError) {
            Timber.e("$TAG: OOM during inference — ${oom.message}")
            statisticalFallback(features)
        }
    }

    fun close() {
        if (hasModel) interpreter?.close()
    }

    // ── Model loading ─────────────────────────────────────────────────────────

    private fun tryLoadInterpreter(): Interpreter? {
        val resourceId = context.resources.getIdentifier(
            MODEL_RESOURCE_NAME, "raw", context.packageName
        )
        if (resourceId == 0) {
            Timber.w("$TAG: '$MODEL_RESOURCE_NAME.tflite' not in res/raw — using statistical fallback")
            return null
        }
        return try {
            val bytes = context.resources.openRawResource(resourceId).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size)
                .apply { order(ByteOrder.nativeOrder()); put(bytes); rewind() }
            val options = Interpreter.Options().apply { numThreads = 2 }
            Interpreter(buffer, options).also {
                Timber.i("$TAG: loaded model ${BuildConfig.ML_MODEL_VERSION} (${bytes.size} bytes)")
            }
        } catch (oom: OutOfMemoryError) {
            Timber.e("$TAG: OOM loading model — ${oom.message}")
            null
        } catch (e: IllegalStateException) {
            Timber.e("$TAG: invalid model state — ${e.message}")
            null
        } catch (e: Exception) {
            Timber.w("$TAG: load failed — ${e.message}")
            null
        }
    }

    // ── Statistical fallback ──────────────────────────────────────────────────

    /**
     * Weighted rule-based risk estimator used when the TFLite model is absent.
     *
     * Feature layout (all values normalised to [0, 1]):
     * ```
     * [0–3]  mean_rt_*              higher → slower → worse
     * [4–8]  accuracy_*             lower  → less accurate → worse
     * [9–10] trend_memory/attention below 0.5 → declining → worse
     * [11]   session_completion_rate lower → worse
     * [12]   rt_variability          higher → worse
     * [13]   delta_from_baseline     lower → greater decline from baseline → worse
     * ```
     */
    private fun statisticalFallback(features: FloatArray): FloatArray {
        if (features.size < INPUT_SIZE) return floatArrayOf(0.5f)

        val rtRisk          = features.slice(0..3).average().toFloat()
        val accRisk         = 1f - features.slice(4..8).average().toFloat()
        val trendDecline    = (1f - (features[9] + features[10]) / 2f).coerceIn(0f, 1f)
        val completionRisk  = 1f - features[11]
        val variabilityRisk = features[12]
        val baselineDecline = 1f - features[13]   // low normalised z-score = larger decline

        val score = (rtRisk          * 0.15f +
                     accRisk         * 0.35f +
                     trendDecline    * 0.20f +
                     completionRisk  * 0.10f +
                     variabilityRisk * 0.10f +
                     baselineDecline * 0.10f).coerceIn(0f, 1f)

        Timber.v("$TAG: fallback score=$score (rt=$rtRisk, acc=$accRisk, trend=$trendDecline)")
        return floatArrayOf(score)
    }
}
