package com.eternamente.app.domain.ml

/**
 * 14-dimensional feature vector consumed by EternaMente's on-device ML model.
 *
 * Feature ordering is fixed and must match the TFLite model's input layer exactly.
 * Use [NAMES] to verify alignment with the Python training pipeline.
 *
 * @property userId     User this vector was computed for.
 * @property extractedAt ISO-8601 date of extraction (UTC).
 * @property weeksBack  Number of weeks of history included in the computation.
 * @property features   Raw (un-normalized) feature values in the canonical order.
 *                      Shape: [FEATURE_COUNT].
 * @property normalized Normalized features in [0, 1] produced by [FeatureNormalizer].
 *                      `null` until [FeatureNormalizer.normalize] is applied.
 */
data class FeatureVector(
    val userId: String,
    val extractedAt: String,
    val weeksBack: Int,
    val features: FloatArray,
    val normalized: FloatArray? = null
) {
    // ── Named accessors (raw) ────────────────────────────────────────────────

    /** [0] Average reaction time (ms) in MEMORY games. */
    val meanRtMemory: Float get() = features[0]

    /** [1] Average reaction time (ms) in ATTENTION games. */
    val meanRtAttention: Float get() = features[1]

    /** [2] Average reaction time (ms) in EXECUTIVE function games. */
    val meanRtExecutive: Float get() = features[2]

    /** [3] Average reaction time (ms) in LANGUAGE games. */
    val meanRtLanguage: Float get() = features[3]

    /** [4] Average accuracy (%) in MEMORY games. */
    val accuracyMemory: Float get() = features[4]

    /** [5] Average accuracy (%) in ATTENTION games. */
    val accuracyAttention: Float get() = features[5]

    /** [6] Average accuracy (%) in EXECUTIVE function games. */
    val accuracyExecutive: Float get() = features[6]

    /** [7] Average accuracy (%) in LANGUAGE games. */
    val accuracyLanguage: Float get() = features[7]

    /** [8] Average accuracy (%) in ORIENTATION games. */
    val accuracyOrientation: Float get() = features[8]

    /** [9] OLS slope of accuracy over time in MEMORY domain. */
    val trendMemory: Float get() = features[9]

    /** [10] OLS slope of accuracy over time in ATTENTION domain. */
    val trendAttention: Float get() = features[10]

    /** [11] Fraction of expected sessions completed in the period [0, 1]. */
    val sessionCompletionRate: Float get() = features[11]

    /** [12] Coefficient of variation (SD/mean) of all reaction times. */
    val rtVariability: Float get() = features[12]

    /** [13] Z-score of current score vs. the user's baseline period. */
    val deltaFromBaseline: Float get() = features[13]

    // ── Equality contracts ────────────────────────────────────────────────────

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FeatureVector) return false
        return userId == other.userId &&
               extractedAt == other.extractedAt &&
               weeksBack == other.weeksBack &&
               features.contentEquals(other.features)
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + extractedAt.hashCode()
        result = 31 * result + weeksBack
        result = 31 * result + features.contentHashCode()
        return result
    }

    companion object {
        const val FEATURE_COUNT = 14

        /** Canonical feature names in index order — must match the Python training pipeline. */
        val NAMES = listOf(
            "mean_rt_memory",          // [0]
            "mean_rt_attention",       // [1]
            "mean_rt_executive",       // [2]
            "mean_rt_language",        // [3]
            "accuracy_memory",         // [4]
            "accuracy_attention",      // [5]
            "accuracy_executive",      // [6]
            "accuracy_language",       // [7]
            "accuracy_orientation",    // [8]
            "trend_memory",            // [9]
            "trend_attention",         // [10]
            "session_completion_rate", // [11]
            "rt_variability",          // [12]
            "delta_from_baseline"      // [13]
        )

        /**
         * Neutral / fallback value used when a feature cannot be computed
         * (fewer than [FeatureExtractor.MIN_DATA_POINTS] sessions in the period).
         */
        const val NEUTRAL = 0.5f
    }
}
