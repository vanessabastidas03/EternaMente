package com.eternamente.app.domain.repository

import com.eternamente.app.domain.model.CognitiveDomain

/**
 * Read-only repository that exposes the SQL aggregates needed exclusively by
 * [com.eternamente.app.domain.ml.FeatureExtractor].
 *
 * Keeping these queries in a dedicated interface prevents them from polluting
 * [GameResultRepository] and [SessionRepository] with ML-specific concerns.
 */
interface FeatureQueryRepository {

    // ── Reaction-time aggregates ──────────────────────────────────────────────

    /**
     * Average reaction time in milliseconds for [domain] since [fromMs].
     * Returns `null` when no qualifying rows exist.
     */
    suspend fun avgRtByDomain(userId: String, domain: CognitiveDomain, fromMs: Long): Float?

    // ── Accuracy aggregates ───────────────────────────────────────────────────

    /**
     * Average accuracy (0–100 %) for [domain] since [fromMs].
     * Returns `null` when no qualifying rows exist.
     */
    suspend fun avgAccuracyByDomain(userId: String, domain: CognitiveDomain, fromMs: Long): Float?

    /**
     * Chronologically-ordered accuracy values for [domain] since [fromMs].
     * Used by [LinearRegression] to compute the accuracy trend slope.
     */
    suspend fun accuracySeriesByDomain(
        userId: String,
        domain: CognitiveDomain,
        fromMs: Long
    ): List<Float>

    // ── RT variability ────────────────────────────────────────────────────────

    /**
     * All positive reaction times across all domains since [fromMs].
     * Used to compute the coefficient of variation (SD / mean).
     */
    suspend fun allRtSince(userId: String, fromMs: Long): List<Float>

    // ── Data-sufficiency guard ────────────────────────────────────────────────

    /**
     * Number of results recorded for [domain] since [fromMs].
     * Used to decide whether a feature can be computed or should fall back to the neutral value.
     */
    suspend fun countByDomainSince(userId: String, domain: CognitiveDomain, fromMs: Long): Int

    // ── Baseline ──────────────────────────────────────────────────────────────

    /**
     * The [limit] earliest normalized scores for the user (chronological order).
     * Treated as the *baseline period* for computing [FeatureVector.deltaFromBaseline].
     */
    suspend fun earliestScores(userId: String, limit: Int = 10): List<Float>

    // ── Session completion rate ────────────────────────────────────────────────

    /**
     * Total sessions *started* (completed or not) in [fromMs]..[toMs].
     * Denominator for session_completion_rate.
     */
    suspend fun countAllSessionsInRange(userId: String, fromMs: Long, toMs: Long): Int

    /**
     * Completed sessions in [fromMs]..[toMs].
     * Numerator for session_completion_rate.
     */
    suspend fun countCompletedSessionsInRange(userId: String, fromMs: Long, toMs: Long): Int
}
