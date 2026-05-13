package com.eternamente.app.domain.ml

import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.repository.FeatureQueryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FeatureExtractor] using synthetic repository stubs (mockk).
 *
 * Each test focuses on a single feature or a specific edge-case (neutral fallback,
 * boundary conditions, CV computation, z-score baseline).
 */
class FeatureExtractorTest {

    private lateinit var repo: FeatureQueryRepository
    private lateinit var extractor: FeatureExtractor

    private val UID   = "user-123"
    private val EPS   = 0.01f
    private val NEUTRAL = FeatureVector.NEUTRAL  // 0.5f

    @Before fun setUp() {
        repo = mockk()
        // Default: return neutral/zero values for all calls so tests can override selectively
        defaultStubs()
        extractor = FeatureExtractor(repo)
    }

    /** Stubs that return "empty" data so any un-configured call doesn't crash. */
    private fun defaultStubs() {
        coEvery { repo.avgRtByDomain(any(), any(), any()) }            returns null
        coEvery { repo.avgAccuracyByDomain(any(), any(), any()) }      returns null
        coEvery { repo.accuracySeriesByDomain(any(), any(), any()) }   returns emptyList()
        coEvery { repo.countByDomainSince(any(), any(), any()) }       returns 0
        coEvery { repo.allRtSince(any(), any()) }                      returns emptyList()
        coEvery { repo.earliestScores(any(), any()) }                  returns emptyList()
        coEvery { repo.countAllSessionsInRange(any(), any(), any()) }  returns 0
        coEvery { repo.countCompletedSessionsInRange(any(), any(), any()) } returns 0
    }

    // ── Neutral fallback ───────────────────────────────────────────────────────

    @Test fun `all features are neutral when no data exists`() = runTest {
        val vec = extractor.extractFeatures(UID)
        vec.features.forEachIndexed { i, v ->
            assertEquals("Feature $i should be neutral, got $v", NEUTRAL, v, EPS)
        }
    }

    // ── [0-3] mean_rt_* ───────────────────────────────────────────────────────

    @Test fun `feature 0 reflects mean RT for MEMORY domain`() = runTest {
        coEvery { repo.avgRtByDomain(UID, CognitiveDomain.MEMORY, any()) } returns 850f

        val vec = extractor.extractFeatures(UID)
        assertEquals(850f, vec.meanRtMemory, EPS)
    }

    @Test fun `feature 1 reflects mean RT for ATTENTION domain`() = runTest {
        coEvery { repo.avgRtByDomain(UID, CognitiveDomain.ATTENTION, any()) } returns 620f

        val vec = extractor.extractFeatures(UID)
        assertEquals(620f, vec.meanRtAttention, EPS)
    }

    @Test fun `RT feature is neutral when domain has no data`() = runTest {
        // EXECUTIVE returns null → neutral
        val vec = extractor.extractFeatures(UID)
        assertEquals(NEUTRAL, vec.meanRtExecutive, EPS)
    }

    // ── [4-8] accuracy_* ──────────────────────────────────────────────────────

    @Test fun `feature 4 reflects average accuracy for MEMORY`() = runTest {
        coEvery { repo.avgAccuracyByDomain(UID, CognitiveDomain.MEMORY, any()) } returns 72f

        val vec = extractor.extractFeatures(UID)
        assertEquals(72f, vec.accuracyMemory, EPS)
    }

    @Test fun `feature 8 reflects average accuracy for ORIENTATION`() = runTest {
        coEvery { repo.avgAccuracyByDomain(UID, CognitiveDomain.ORIENTATION, any()) } returns 88f

        val vec = extractor.extractFeatures(UID)
        assertEquals(88f, vec.accuracyOrientation, EPS)
    }

    @Test fun `accuracy feature is neutral when no data for domain`() = runTest {
        val vec = extractor.extractFeatures(UID)
        assertEquals(NEUTRAL, vec.accuracyLanguage, EPS)
    }

    // ── [9-10] trend_* ────────────────────────────────────────────────────────

    @Test fun `trend_memory is neutral when fewer than MIN_DATA_POINTS`() = runTest {
        coEvery { repo.countByDomainSince(UID, CognitiveDomain.MEMORY, any()) } returns 2
        coEvery { repo.accuracySeriesByDomain(UID, CognitiveDomain.MEMORY, any()) } returns listOf(50f, 60f)

        val vec = extractor.extractFeatures(UID)
        assertEquals(NEUTRAL, vec.trendMemory, EPS)
    }

    @Test fun `trend_memory is positive for improving accuracy series`() = runTest {
        val series = listOf(40f, 50f, 60f, 70f, 80f)  // slope = +10
        coEvery { repo.countByDomainSince(UID, CognitiveDomain.MEMORY, any()) } returns 5
        coEvery { repo.accuracySeriesByDomain(UID, CognitiveDomain.MEMORY, any()) } returns series

        val vec = extractor.extractFeatures(UID)
        assertTrue("Expected positive trend, got ${vec.trendMemory}", vec.trendMemory > 0f)
    }

    @Test fun `trend_attention is negative for declining accuracy series`() = runTest {
        val series = listOf(80f, 70f, 60f, 50f, 40f)  // slope = -10
        coEvery { repo.countByDomainSince(UID, CognitiveDomain.ATTENTION, any()) } returns 5
        coEvery { repo.accuracySeriesByDomain(UID, CognitiveDomain.ATTENTION, any()) } returns series

        val vec = extractor.extractFeatures(UID)
        assertTrue("Expected negative trend, got ${vec.trendAttention}", vec.trendAttention < 0f)
    }

    @Test fun `trend is zero for flat accuracy series`() = runTest {
        val series = listOf(60f, 60f, 60f, 60f)
        coEvery { repo.countByDomainSince(UID, CognitiveDomain.MEMORY, any()) } returns 4
        coEvery { repo.accuracySeriesByDomain(UID, CognitiveDomain.MEMORY, any()) } returns series

        val vec = extractor.extractFeatures(UID)
        assertEquals(0f, vec.trendMemory, EPS)
    }

    // ── [11] session_completion_rate ──────────────────────────────────────────

    @Test fun `session_completion_rate is 1 when all sessions completed`() = runTest {
        coEvery { repo.countAllSessionsInRange(UID, any(), any()) }       returns 10
        coEvery { repo.countCompletedSessionsInRange(UID, any(), any()) } returns 10

        val vec = extractor.extractFeatures(UID)
        assertEquals(1f, vec.sessionCompletionRate, EPS)
    }

    @Test fun `session_completion_rate is 0_5 when half completed`() = runTest {
        coEvery { repo.countAllSessionsInRange(UID, any(), any()) }       returns 10
        coEvery { repo.countCompletedSessionsInRange(UID, any(), any()) } returns 5

        val vec = extractor.extractFeatures(UID)
        assertEquals(0.5f, vec.sessionCompletionRate, EPS)
    }

    @Test fun `session_completion_rate is neutral when no sessions`() = runTest {
        // Both return 0 → neutral
        val vec = extractor.extractFeatures(UID)
        assertEquals(NEUTRAL, vec.sessionCompletionRate, EPS)
    }

    @Test fun `session_completion_rate is clamped to 1 even if completed exceeds total`() = runTest {
        // Edge case: data inconsistency
        coEvery { repo.countAllSessionsInRange(UID, any(), any()) }       returns 5
        coEvery { repo.countCompletedSessionsInRange(UID, any(), any()) } returns 8

        val vec = extractor.extractFeatures(UID)
        assertEquals(1f, vec.sessionCompletionRate, EPS)
    }

    // ── [12] rt_variability ───────────────────────────────────────────────────

    @Test fun `rt_variability is neutral when fewer than MIN_DATA_POINTS RT values`() = runTest {
        coEvery { repo.allRtSince(UID, any()) } returns listOf(500f, 600f)  // only 2

        val vec = extractor.extractFeatures(UID)
        assertEquals(NEUTRAL, vec.rtVariability, EPS)
    }

    @Test fun `rt_variability is 0 when all RT values are equal`() = runTest {
        coEvery { repo.allRtSince(UID, any()) } returns listOf(800f, 800f, 800f, 800f)

        val vec = extractor.extractFeatures(UID)
        assertEquals(0f, vec.rtVariability, EPS)
    }

    @Test fun `rt_variability matches expected CV`() = runTest {
        // values [2,4,4,4,5,5,7,9]: mean=5, std=2 → CV=0.4
        coEvery { repo.allRtSince(UID, any()) } returns
            listOf(2f, 4f, 4f, 4f, 5f, 5f, 7f, 9f)

        val vec = extractor.extractFeatures(UID)
        assertEquals(0.4f, vec.rtVariability, EPS)
    }

    // ── [13] delta_from_baseline ──────────────────────────────────────────────

    @Test fun `delta_from_baseline is neutral when no baseline data`() = runTest {
        coEvery { repo.earliestScores(UID, any()) } returns emptyList()

        val vec = extractor.extractFeatures(UID)
        assertEquals(NEUTRAL, vec.deltaFromBaseline, EPS)
    }

    @Test fun `delta_from_baseline is neutral when fewer than MIN_DATA_POINTS baseline scores`() = runTest {
        coEvery { repo.earliestScores(UID, any()) } returns listOf(60f, 65f)  // only 2

        val vec = extractor.extractFeatures(UID)
        assertEquals(NEUTRAL, vec.deltaFromBaseline, EPS)
    }

    @Test fun `delta_from_baseline is 0 when recent equals baseline mean`() = runTest {
        // Baseline: [60,60,60,...] mean=60, std=0 → zScore=0f (zero std guard)
        coEvery { repo.earliestScores(UID, any()) } returns List(15) { 60f }

        val vec = extractor.extractFeatures(UID)
        assertEquals(0f, vec.deltaFromBaseline, EPS)
    }

    @Test fun `delta_from_baseline is positive when recent is above baseline`() = runTest {
        // Baseline: mean~50, std~5. Recent: mean~70 → positive z-score
        val baseline = List(15) { i -> 48f + i * 0.2f }   // ~50 with small variance
        val recent   = List(15) { 70f }                    // well above baseline
        coEvery { repo.earliestScores(UID, 15) }           returns baseline
        coEvery { repo.earliestScores(UID, 30) }           returns (baseline + recent)

        val vec = extractor.extractFeatures(UID)
        assertTrue("Expected positive delta, got ${vec.deltaFromBaseline}", vec.deltaFromBaseline > 0f)
    }

    // ── FeatureVector metadata ────────────────────────────────────────────────

    @Test fun `extracted vector has correct feature count`() = runTest {
        val vec = extractor.extractFeatures(UID)
        assertEquals(FeatureVector.FEATURE_COUNT, vec.features.size)
    }

    @Test fun `extracted vector has correct userId`() = runTest {
        val vec = extractor.extractFeatures(UID)
        assertEquals(UID, vec.userId)
    }

    @Test fun `NAMES list has correct size`() {
        assertEquals(FeatureVector.FEATURE_COUNT, FeatureVector.NAMES.size)
    }

    // ── Integration: normalize after extract ──────────────────────────────────

    @Test fun `extracted features normalize to 0-1 range`() = runTest {
        // Provide realistic values
        coEvery { repo.avgRtByDomain(UID, CognitiveDomain.MEMORY, any()) }    returns 1200f
        coEvery { repo.avgAccuracyByDomain(UID, CognitiveDomain.MEMORY, any()) } returns 70f
        coEvery { repo.countByDomainSince(UID, CognitiveDomain.MEMORY, any()) } returns 5
        coEvery { repo.accuracySeriesByDomain(UID, CognitiveDomain.MEMORY, any()) } returns
            listOf(60f, 65f, 68f, 72f, 75f)
        coEvery { repo.allRtSince(UID, any()) } returns listOf(900f, 1100f, 1200f, 1300f, 1500f)
        coEvery { repo.countAllSessionsInRange(UID, any(), any()) }       returns 10
        coEvery { repo.countCompletedSessionsInRange(UID, any(), any()) } returns 8

        val raw  = extractor.extractFeatures(UID)
        val norm = FeatureNormalizer.normalize(raw)

        assertNotNull(norm.normalized)
        norm.normalized!!.forEachIndexed { i, v ->
            assertTrue("Normalized feature $i = $v not in [0,1]", v in 0f..1f)
        }
    }
}
