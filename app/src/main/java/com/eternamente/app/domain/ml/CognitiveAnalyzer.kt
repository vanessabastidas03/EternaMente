package com.eternamente.app.domain.ml

import com.eternamente.app.BuildConfig
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full cognitive-change analysis pipeline for a single user.
 *
 * Pipeline (all on [Dispatchers.Default]):
 * 1. [FeatureExtractor] → extract 14 raw features from the last 4 weeks of Room data.
 * 2. [FeatureNormalizer] → map raw features to [0, 1].
 * 3. [IsolationForestModel] → append vector to user history, rebuild forest, compute anomaly score.
 * 4. [TFLiteModelManager] → run TFLite model (or statistical fallback).
 * 5. Determine [AlertLevel] from anomaly score + baseline delta + domain flags.
 * 6. [AlertGenerator] → produce a safe, human-readable Spanish explanation.
 *
 * @see CognitiveAnalysisResult for the full output contract.
 */
@Singleton
class CognitiveAnalyzer @Inject constructor(
    private val featureExtractor:  FeatureExtractor,
    private val isolationForest:   IsolationForestModel,
    private val tfliteManager:     TFLiteModelManager
) {

    companion object {
        private const val TAG = "CognitiveAnalyzer"

        // Domain flagging thresholds (on normalised [0, 1] scale)
        private const val ACC_LOW_THRESHOLD    = 0.35f  // normalised accuracy < 35 %
        private const val RT_HIGH_THRESHOLD    = 0.70f  // normalised RT > ~3 600 ms
        private const val TREND_LOW_THRESHOLD  = 0.35f  // significant declining trend

        // AlertLevel cut-points
        private const val ANOMALY_ALERT_CUTOFF = 0.70f
        private const val ANOMALY_WATCH_CUTOFF = 0.40f
        private const val DECLINE_ALERT_SD     = 1.5f   // z-score units below baseline
        private const val DECLINE_WATCH_SD     = 1.0f
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs the full pipeline and returns a [CognitiveAnalysisResult].
     *
     * Suspends on [Dispatchers.Default]; safe to call from any coroutine context.
     * Feature extraction internally switches to [Dispatchers.IO] for Room queries.
     */
    suspend fun analyze(userId: String): CognitiveAnalysisResult = withContext(Dispatchers.Default) {
        val pipelineStart = System.currentTimeMillis()
        // Seguridad: loguear solo el prefijo del UUID para correlación sin exponer el ID completo.
        Timber.d("$TAG: analysis started for user ${userId.take(8)}…")

        // 1 + 2 — Feature extraction and normalisation
        val rawVector  = featureExtractor.extractFeatures(userId)
        val normVector = FeatureNormalizer.normalize(rawVector)

        // 3 — Update Isolation Forest history and score
        val trained = isolationForest.addAndRetrain(normVector)
        val anomalyScore = if (trained) {
            isolationForest.anomalyScore(normVector)
        } else {
            Timber.d("$TAG: IF not yet trained (${isolationForest.historySizeSnapshot}/${IsolationForestModel.MIN_SAMPLES}), using neutral score")
            0.5f
        }

        // 4 — TFLite inference (or statistical fallback)
        val inferenceInput  = normVector.normalized ?: FloatArray(FeatureVector.FEATURE_COUNT) { 0.5f }
        val inferenceOutput = tfliteManager.runInference(inferenceInput)
        val modelRiskScore  = inferenceOutput.firstOrNull()?.coerceIn(0f, 1f) ?: 0.5f

        // 5 — Alert classification
        val flaggedDomains = identifyFlaggedDomains(normVector)
        val alertLevel     = determineAlertLevel(anomalyScore, rawVector, flaggedDomains)

        // 6 — Explanation
        val explanation = AlertGenerator.generate(alertLevel, flaggedDomains)

        val result = CognitiveAnalysisResult(
            userId          = userId,
            analyzedAt      = System.currentTimeMillis(),
            featureVector   = normVector,
            anomalyScore    = anomalyScore,
            tfliteRiskScore = modelRiskScore,
            alertLevel      = alertLevel,
            flaggedDomains  = flaggedDomains,
            explanation     = explanation,
            modelUsed       = if (tfliteManager.hasModel)
                                  "tflite_${BuildConfig.ML_MODEL_VERSION}"
                              else "statistical_fallback"
        )

        val pipelineMs = System.currentTimeMillis() - pipelineStart
        Timber.i(
            "$TAG: done in ${pipelineMs}ms — level=${alertLevel}, anomaly=%.2f, model=%.2f, domains=${flaggedDomains.size}"
                .format(anomalyScore, modelRiskScore)
        )
        result
    }

    // ── Domain flagging ───────────────────────────────────────────────────────

    /**
     * Returns the list of cognitive domains whose normalised features deviate
     * significantly from expected healthy ranges.
     *
     * A domain is flagged if its accuracy is very low, its reaction time is very slow,
     * or its trend is strongly declining (memory and attention only, since those are
     * the only domains with OLS trend features).
     */
    private fun identifyFlaggedDomains(normVector: FeatureVector): List<CognitiveDomain> {
        val n = normVector.normalized ?: return emptyList()
        return buildList {
            if (n[4] < ACC_LOW_THRESHOLD || n[0] > RT_HIGH_THRESHOLD || n[9]  < TREND_LOW_THRESHOLD) add(CognitiveDomain.MEMORY)
            if (n[5] < ACC_LOW_THRESHOLD || n[1] > RT_HIGH_THRESHOLD || n[10] < TREND_LOW_THRESHOLD) add(CognitiveDomain.ATTENTION)
            if (n[6] < ACC_LOW_THRESHOLD || n[2] > RT_HIGH_THRESHOLD)                                add(CognitiveDomain.EXECUTIVE)
            if (n[7] < ACC_LOW_THRESHOLD || n[3] > RT_HIGH_THRESHOLD)                                add(CognitiveDomain.LANGUAGE)
            if (n[8] < ACC_LOW_THRESHOLD)                                                             add(CognitiveDomain.ORIENTATION)
        }
    }

    // ── Alert level determination ─────────────────────────────────────────────

    /**
     * Classifies risk into [AlertLevel] using two independent signals:
     *
     * - **Isolation Forest anomaly score** — how unusual this week is vs. the user's history.
     * - **Baseline decline** — z-score of recent performance vs. earliest recorded sessions.
     *
     * Rules (first match wins, in decreasing severity order):
     * ```
     * ALERT  : anomaly > 0.70  OR  decline > 1.5 SD  AND  ≥ 2 domains flagged
     * WATCH  : anomaly ∈ [0.40, 0.70]  OR  decline ∈ [1.0, 1.5] SD
     * NORMAL : everything else
     * ```
     */
    private fun determineAlertLevel(
        anomalyScore:  Float,
        rawVector:     FeatureVector,
        flaggedDomains: List<CognitiveDomain>
    ): AlertLevel {
        // feature[13] = z-score of recent vs. baseline; negative = decline
        val declineSd = (-rawVector.deltaFromBaseline).coerceAtLeast(0f)

        return when {
            anomalyScore > ANOMALY_ALERT_CUTOFF ||
                (declineSd > DECLINE_ALERT_SD && flaggedDomains.size >= 2) -> AlertLevel.ALERT

            anomalyScore >= ANOMALY_WATCH_CUTOFF ||
                declineSd >= DECLINE_WATCH_SD -> AlertLevel.WATCH

            else -> AlertLevel.NORMAL
        }
    }
}
