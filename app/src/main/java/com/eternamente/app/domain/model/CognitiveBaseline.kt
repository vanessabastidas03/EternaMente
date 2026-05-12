package com.eternamente.app.domain.model

/**
 * Reference cognitive profile established from the user's initial [SessionType.BASELINE] session.
 *
 * All scores are normalized to a 0–100 scale relative to the user's age and education cohort.
 * The baseline is the anchor against which longitudinal decline is measured.
 *
 * @property userId Identifier of the [User] who owns this baseline.
 * @property memoryScore Baseline score for the [CognitiveDomain.MEMORY] domain.
 * @property attentionScore Baseline score for the [CognitiveDomain.ATTENTION] domain.
 * @property executiveScore Baseline score for the [CognitiveDomain.EXECUTIVE] domain.
 * @property languageScore Baseline score for the [CognitiveDomain.LANGUAGE] domain.
 * @property orientationScore Baseline score for the [CognitiveDomain.ORIENTATION] domain.
 * @property overallScore Weighted composite score across all five domains.
 *   Weights are defined by the clinical protocol and stored in the ML model metadata.
 * @property calculatedAt Timestamp when this baseline was computed in epoch milliseconds.
 */
data class CognitiveBaseline(
    val userId: String,
    val memoryScore: Float,
    val attentionScore: Float,
    val executiveScore: Float,
    val languageScore: Float,
    val orientationScore: Float,
    val overallScore: Float,
    val calculatedAt: Long
) {

    /**
     * Returns a map of [CognitiveDomain] to its corresponding baseline score,
     * convenient for chart rendering and ML feature construction.
     */
    fun asScoreMap(): Map<CognitiveDomain, Float> = mapOf(
        CognitiveDomain.MEMORY           to memoryScore,
        CognitiveDomain.ATTENTION        to attentionScore,
        CognitiveDomain.EXECUTIVE        to executiveScore,
        CognitiveDomain.LANGUAGE         to languageScore,
        CognitiveDomain.ORIENTATION      to orientationScore
    )

    /**
     * Returns the lowest-scoring domain, which typically receives priority
     * in the daily session game selection algorithm.
     */
    fun weakestDomain(): CognitiveDomain =
        asScoreMap().minByOrNull { it.value }?.key ?: CognitiveDomain.MEMORY
}
