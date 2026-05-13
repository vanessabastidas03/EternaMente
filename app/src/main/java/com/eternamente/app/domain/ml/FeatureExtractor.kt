package com.eternamente.app.domain.ml

import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.repository.FeatureQueryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transforms raw game-result and session data from Room into a [FeatureVector]
 * ready for the TFLite cognitive-risk model.
 *
 * All 14 features are computed concurrently on [Dispatchers.IO] to minimise
 * wall-clock latency. Features that cannot be computed (< [MIN_DATA_POINTS]
 * qualifying rows) fall back to [FeatureVector.NEUTRAL] = 0.5.
 *
 * Calling [extractFeatures] does **not** run normalisation — call
 * [FeatureNormalizer.normalize] separately before feeding to the model.
 *
 * @param featureRepo Provides all the SQL aggregates needed for feature computation.
 */
@Singleton
class FeatureExtractor @Inject constructor(
    private val featureRepo: FeatureQueryRepository
) {

    // ── Configuration ─────────────────────────────────────────────────────────

    /** Number of earliest scores used to characterise the baseline period. */
    private val baselineLimit = 15

    companion object {
        /** Minimum rows for a domain to compute a real feature instead of the neutral value. */
        const val MIN_DATA_POINTS = 3
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Extracts and returns a 14-feature vector for [userId] using the last [weeksBack] weeks.
     *
     * @param userId    UUID of the user to evaluate.
     * @param weeksBack History window in weeks (default 4 = ~1 month).
     * @return [FeatureVector] with raw (un-normalized) values.
     */
    suspend fun extractFeatures(userId: String, weeksBack: Int = 4): FeatureVector =
        withContext(Dispatchers.IO) {
            val zone     = ZoneId.systemDefault()
            val now      = LocalDate.now(zone)
            val fromMs   = now.minusWeeks(weeksBack.toLong())
                               .atStartOfDay(zone).toInstant().toEpochMilli()
            val toMs     = now.atStartOfDay(zone).toInstant().toEpochMilli() +
                           TimeUnit.DAYS.toMillis(1) - 1L

            // ── Launch all DB queries concurrently ────────────────────────────
            val rtMem  = async { featureRepo.avgRtByDomain(userId, CognitiveDomain.MEMORY,   fromMs) }
            val rtAtt  = async { featureRepo.avgRtByDomain(userId, CognitiveDomain.ATTENTION, fromMs) }
            val rtExec = async { featureRepo.avgRtByDomain(userId, CognitiveDomain.EXECUTIVE, fromMs) }
            val rtLang = async { featureRepo.avgRtByDomain(userId, CognitiveDomain.LANGUAGE,  fromMs) }

            val accMem  = async { featureRepo.avgAccuracyByDomain(userId, CognitiveDomain.MEMORY,       fromMs) }
            val accAtt  = async { featureRepo.avgAccuracyByDomain(userId, CognitiveDomain.ATTENTION,    fromMs) }
            val accExec = async { featureRepo.avgAccuracyByDomain(userId, CognitiveDomain.EXECUTIVE,    fromMs) }
            val accLang = async { featureRepo.avgAccuracyByDomain(userId, CognitiveDomain.LANGUAGE,     fromMs) }
            val accOri  = async { featureRepo.avgAccuracyByDomain(userId, CognitiveDomain.ORIENTATION,  fromMs) }

            val cntMem  = async { featureRepo.countByDomainSince(userId, CognitiveDomain.MEMORY,   fromMs) }
            val cntAtt  = async { featureRepo.countByDomainSince(userId, CognitiveDomain.ATTENTION, fromMs) }

            val serMem  = async { featureRepo.accuracySeriesByDomain(userId, CognitiveDomain.MEMORY,   fromMs) }
            val serAtt  = async { featureRepo.accuracySeriesByDomain(userId, CognitiveDomain.ATTENTION, fromMs) }

            val allRt      = async { featureRepo.allRtSince(userId, fromMs) }
            val baseline   = async { featureRepo.earliestScores(userId, baselineLimit) }
            val sessAll    = async { featureRepo.countAllSessionsInRange(userId, fromMs, toMs) }
            val sessCompl  = async { featureRepo.countCompletedSessionsInRange(userId, fromMs, toMs) }

            // ── Await and compute ─────────────────────────────────────────────
            val neutral = FeatureVector.NEUTRAL

            // [0-3] Mean RT per domain
            val f0 = rtMem.await()  ?: neutral
            val f1 = rtAtt.await()  ?: neutral
            val f2 = rtExec.await() ?: neutral
            val f3 = rtLang.await() ?: neutral

            // [4-8] Mean accuracy per domain
            val f4 = accMem.await()  ?: neutral
            val f5 = accAtt.await()  ?: neutral
            val f6 = accExec.await() ?: neutral
            val f7 = accLang.await() ?: neutral
            val f8 = accOri.await()  ?: neutral

            // [9] trend_memory — OLS slope, neutral if < MIN_DATA_POINTS
            val memSeries = serMem.await()
            val f9 = if (cntMem.await() >= MIN_DATA_POINTS) {
                LinearRegression.slope(memSeries)
            } else neutral

            // [10] trend_attention
            val attSeries = serAtt.await()
            val f10 = if (cntAtt.await() >= MIN_DATA_POINTS) {
                LinearRegression.slope(attSeries)
            } else neutral

            // [11] session_completion_rate
            val totalSessions = sessAll.await()
            val f11 = if (totalSessions > 0) {
                (sessCompl.await().toFloat() / totalSessions).coerceIn(0f, 1f)
            } else neutral

            // [12] rt_variability — coefficient of variation
            val rtValues = allRt.await()
            val f12 = if (rtValues.size >= MIN_DATA_POINTS) {
                LinearRegression.coefficientOfVariation(rtValues).coerceIn(0f, Float.MAX_VALUE)
            } else neutral

            // [13] delta_from_baseline — z-score of recent mean vs. baseline
            val baselineScores = baseline.await()
            val f13 = computeDeltaFromBaseline(
                userId   = userId,
                fromMs   = fromMs,
                baseline = baselineScores
            )

            val features = floatArrayOf(f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13)

            FeatureVector(
                userId       = userId,
                extractedAt  = LocalDate.now(zone).toString(),
                weeksBack    = weeksBack,
                features     = features
            )
        }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Computes the z-score of the user's recent normalized scores relative
     * to their earliest [baselineLimit] scores.
     *
     * Returns [FeatureVector.NEUTRAL] when insufficient baseline data exists.
     */
    private suspend fun computeDeltaFromBaseline(
        userId: String,
        @Suppress("UNUSED_PARAMETER") fromMs: Long,
        baseline: List<Float>
    ): Float {
        if (baseline.size < MIN_DATA_POINTS) return FeatureVector.NEUTRAL

        val (baselineMean, baselineStd) = LinearRegression.meanAndStd(baseline)

        // Recent scores in the window
        val recentScores = featureRepo.earliestScores(userId, baselineLimit * 2)
            .takeLast(baselineLimit)

        if (recentScores.size < MIN_DATA_POINTS) return FeatureVector.NEUTRAL

        val (recentMean, _) = LinearRegression.meanAndStd(recentScores)
        return LinearRegression.zScore(recentMean, baselineMean, baselineStd)
    }
}
