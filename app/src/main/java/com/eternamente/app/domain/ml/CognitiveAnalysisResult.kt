package com.eternamente.app.domain.ml

import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain

/**
 * Full output of a single cognitive analysis run produced by [CognitiveAnalyzer].
 *
 * Combines the Isolation Forest anomaly score, on-device TFLite (or statistical fallback)
 * risk score, alert classification, and a human-readable Spanish explanation.
 */
data class CognitiveAnalysisResult(
    val userId: String,

    /** Epoch-milliseconds when the analysis was computed. */
    val analyzedAt: Long,

    /** Normalized 14-feature vector used for this analysis run. */
    val featureVector: FeatureVector,

    /** Isolation Forest anomaly score [0, 1]. Values > 0.7 indicate anomaly. */
    val anomalyScore: Float,

    /** TFLite model or statistical fallback risk score [0, 1]. */
    val tfliteRiskScore: Float,

    val alertLevel: AlertLevel,

    /** Cognitive domains whose metrics deviated significantly from expected ranges. */
    val flaggedDomains: List<CognitiveDomain>,

    /** Human-readable Spanish explanation (no clinical terminology). */
    val explanation: String,

    /** Tag identifying inference path: "tflite_vX" or "statistical_fallback". */
    val modelUsed: String
) {
    /** Blended risk score suitable for persisting in [com.eternamente.app.domain.model.MlPrediction]. */
    val combinedRiskScore: Float get() = ((anomalyScore + tfliteRiskScore) / 2f).coerceIn(0f, 1f)
}
