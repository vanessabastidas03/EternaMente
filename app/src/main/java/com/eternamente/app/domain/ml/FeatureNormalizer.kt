package com.eternamente.app.domain.ml

/**
 * Min-max normalizer that maps each feature to [0, 1].
 *
 * The [MIN] and [MAX] arrays contain the normalization bounds derived from the
 * cognitive-assessment training dataset (older-adult cohort, n=1200).
 * Values outside the training range are clamped — they do NOT cause errors.
 *
 * ## Normalization bounds rationale
 *
 * | Index | Feature                  | Min    | Max    | Notes                                          |
 * |-------|--------------------------|--------|--------|------------------------------------------------|
 * | 0–3   | mean_rt_* (ms)           | 200    | 5 000  | Feasible RT for adults 60+                     |
 * | 4–8   | accuracy_* (%)           | 0      | 100    | Direct percentage                              |
 * | 9–10  | trend_* (slope)          | −10    | 10     | Max change of ±10 pp/session in training data  |
 * | 11    | session_completion_rate  | 0      | 1      | Already bounded; passthrough                   |
 * | 12    | rt_variability (CV)      | 0      | 1.5    | CV > 1.5 indicates severe inconsistency        |
 * | 13    | delta_from_baseline      | −3     | 3      | Clipped z-score range                          |
 */
object FeatureNormalizer {

    // Training-derived normalization bounds — MUST match Python pipeline constants.
    private val MIN = floatArrayOf(
        200f, 200f, 200f, 200f,   // [0-3]  mean_rt_* (ms)
        0f, 0f, 0f, 0f, 0f,       // [4-8]  accuracy_* (%)
        -10f, -10f,               // [9-10] trend_*
        0f,                       // [11]   session_completion_rate
        0f,                       // [12]   rt_variability
        -3f                       // [13]   delta_from_baseline
    )

    private val MAX = floatArrayOf(
        5_000f, 5_000f, 5_000f, 5_000f,  // [0-3]
        100f, 100f, 100f, 100f, 100f,     // [4-8]
        10f, 10f,                          // [9-10]
        1f,                                // [11]
        1.5f,                              // [12]
        3f                                 // [13]
    )

    init {
        require(MIN.size == FeatureVector.FEATURE_COUNT)
        require(MAX.size == FeatureVector.FEATURE_COUNT)
    }

    /**
     * Normalizes a single raw value for feature at [index] to [0, 1].
     * Values below [MIN][index] return 0f; values above [MAX][index] return 1f.
     */
    fun normalizeAt(index: Int, rawValue: Float): Float {
        val lo = MIN[index]; val hi = MAX[index]
        if (hi == lo) return 0f                     // degenerate range guard
        return ((rawValue - lo) / (hi - lo)).coerceIn(0f, 1f)
    }

    /**
     * Returns a copy of [vector] with [FeatureVector.normalized] populated.
     * The raw [FeatureVector.features] are unchanged.
     */
    fun normalize(vector: FeatureVector): FeatureVector {
        val norm = FloatArray(FeatureVector.FEATURE_COUNT) { i ->
            normalizeAt(i, vector.features[i])
        }
        return vector.copy(normalized = norm)
    }

    /**
     * Denormalizes a normalized value back to its raw scale.
     * Inverse of [normalizeAt] — useful for human-readable display.
     */
    fun denormalizeAt(index: Int, normalizedValue: Float): Float {
        val lo = MIN[index]; val hi = MAX[index]
        return lo + normalizedValue.coerceIn(0f, 1f) * (hi - lo)
    }

    /** Exposes bounds for tests and diagnostics. */
    val minBounds: FloatArray get() = MIN.copyOf()
    val maxBounds: FloatArray get() = MAX.copyOf()
}
