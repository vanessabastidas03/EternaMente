package com.eternamente.app.domain.ml

/**
 * Ordinary Least Squares linear regression utilities.
 *
 * Implemented as a pure-Kotlin object so it can be called from any layer
 * without Android dependencies or coroutines.
 */
object LinearRegression {

    /**
     * Computes the OLS slope of [y] values measured at integer time indices 0, 1, 2, … n-1.
     *
     * Formula: β = (n·Σ(i·yᵢ) − Σi·Σyᵢ) / (n·Σi² − (Σi)²)
     *
     * - Returns `0f` for empty lists or single-element lists (undefined slope).
     * - Returns `0f` when all y-values are identical (zero denominator).
     *
     * @param y Time-ordered values (oldest first).
     * @return Slope in units of [y] per step. Positive = improving, negative = declining.
     */
    fun slope(y: List<Float>): Float {
        val n = y.size
        if (n < 2) return 0f

        var sumX  = 0.0; var sumY  = 0.0
        var sumXY = 0.0; var sumX2 = 0.0

        for (i in 0 until n) {
            val x = i.toDouble()
            val v = y[i].toDouble()
            sumX  += x
            sumY  += v
            sumXY += x * v
            sumX2 += x * x
        }

        val denom = n * sumX2 - sumX * sumX
        return if (denom == 0.0) 0f else ((n * sumXY - sumX * sumY) / denom).toFloat()
    }

    /**
     * Returns the mean and standard deviation of [values].
     * Used internally to compute [coefficientOfVariation].
     *
     * @return Pair(mean, stdDev). Both are `0f` for empty or single-element lists.
     */
    fun meanAndStd(values: List<Float>): Pair<Float, Float> {
        if (values.isEmpty()) return 0f to 0f
        val mean = values.sum() / values.size
        if (values.size == 1) return mean to 0f
        val variance = values.sumOf { v -> ((v - mean) * (v - mean)).toDouble() } / values.size
        return mean to Math.sqrt(variance).toFloat()
    }

    /**
     * Coefficient of Variation = stdDev / mean.
     * Measures relative variability of a series — used for `rt_variability` feature.
     *
     * @return CV ≥ 0, or `0f` when mean is zero.
     */
    fun coefficientOfVariation(values: List<Float>): Float {
        val (mean, std) = meanAndStd(values)
        return if (mean == 0f) 0f else std / mean
    }

    /**
     * Z-score of [value] relative to [referenceMean] and [referenceStd].
     * Clipped to [-3, 3] to suppress extreme outliers.
     *
     * @return Z-score in [-3, 3], or `0f` when [referenceStd] is zero.
     */
    fun zScore(value: Float, referenceMean: Float, referenceStd: Float): Float {
        if (referenceStd == 0f) return 0f
        return ((value - referenceMean) / referenceStd).coerceIn(-3f, 3f)
    }
}
