package com.eternamente.app.domain.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [FeatureNormalizer].
 *
 * Verifies that each of the 14 features is correctly mapped to [0, 1],
 * clamped at boundaries, and denormalized back to the original scale.
 */
class FeatureNormalizerTest {

    private val EPS = 0.001f

    // ── normalizeAt() ─────────────────────────────────────────────────────────

    @Test fun `RT at min bound normalizes to 0`() {
        // Feature [0] mean_rt_memory, min = 200ms
        assertEquals(0f, FeatureNormalizer.normalizeAt(0, 200f), EPS)
    }

    @Test fun `RT at max bound normalizes to 1`() {
        // Feature [0] mean_rt_memory, max = 5000ms
        assertEquals(1f, FeatureNormalizer.normalizeAt(0, 5_000f), EPS)
    }

    @Test fun `RT midpoint normalizes to 0_5`() {
        // mid = (200 + 5000) / 2 = 2600ms → 0.5
        assertEquals(0.5f, FeatureNormalizer.normalizeAt(0, 2_600f), EPS)
    }

    @Test fun `RT below min is clamped to 0`() {
        assertEquals(0f, FeatureNormalizer.normalizeAt(0, 0f), EPS)
    }

    @Test fun `RT above max is clamped to 1`() {
        assertEquals(1f, FeatureNormalizer.normalizeAt(0, 99_000f), EPS)
    }

    @Test fun `accuracy 0 percent normalizes to 0`() {
        // Feature [4] accuracy_memory
        assertEquals(0f, FeatureNormalizer.normalizeAt(4, 0f), EPS)
    }

    @Test fun `accuracy 100 percent normalizes to 1`() {
        assertEquals(1f, FeatureNormalizer.normalizeAt(4, 100f), EPS)
    }

    @Test fun `accuracy 50 percent normalizes to 0_5`() {
        assertEquals(0.5f, FeatureNormalizer.normalizeAt(4, 50f), EPS)
    }

    @Test fun `trend zero slope normalizes to 0_5`() {
        // Feature [9] trend_memory: min=-10, max=10 → 0 maps to 0.5
        assertEquals(0.5f, FeatureNormalizer.normalizeAt(9, 0f), EPS)
    }

    @Test fun `trend negative max normalizes to 0`() {
        assertEquals(0f, FeatureNormalizer.normalizeAt(9, -10f), EPS)
    }

    @Test fun `trend positive max normalizes to 1`() {
        assertEquals(1f, FeatureNormalizer.normalizeAt(9, 10f), EPS)
    }

    @Test fun `session_completion_rate 0 normalizes to 0`() {
        assertEquals(0f, FeatureNormalizer.normalizeAt(11, 0f), EPS)
    }

    @Test fun `session_completion_rate 1 normalizes to 1`() {
        assertEquals(1f, FeatureNormalizer.normalizeAt(11, 1f), EPS)
    }

    @Test fun `rt_variability 0 normalizes to 0`() {
        assertEquals(0f, FeatureNormalizer.normalizeAt(12, 0f), EPS)
    }

    @Test fun `rt_variability at max normalizes to 1`() {
        // max = 1.5
        assertEquals(1f, FeatureNormalizer.normalizeAt(12, 1.5f), EPS)
    }

    @Test fun `delta_from_baseline zero z-score normalizes to 0_5`() {
        // Feature [13]: min=-3, max=3 → 0 maps to 0.5
        assertEquals(0.5f, FeatureNormalizer.normalizeAt(13, 0f), EPS)
    }

    @Test fun `delta_from_baseline minus3 normalizes to 0`() {
        assertEquals(0f, FeatureNormalizer.normalizeAt(13, -3f), EPS)
    }

    @Test fun `delta_from_baseline plus3 normalizes to 1`() {
        assertEquals(1f, FeatureNormalizer.normalizeAt(13, 3f), EPS)
    }

    // ── normalize(vector) ────────────────────────────────────────────────────

    @Test fun `normalize returns all features in 0-1`() {
        val rawFeatures = floatArrayOf(
            1200f,  // mean_rt_memory
            1500f,  // mean_rt_attention
            1800f,  // mean_rt_executive
            2000f,  // mean_rt_language
            70f,    // accuracy_memory
            65f,    // accuracy_attention
            55f,    // accuracy_executive
            80f,    // accuracy_language
            60f,    // accuracy_orientation
            1.5f,   // trend_memory
            -0.5f,  // trend_attention
            0.75f,  // session_completion_rate
            0.3f,   // rt_variability
            0.5f    // delta_from_baseline
        )
        val vec = FeatureVector("u1", "2026-01-01", 4, rawFeatures)
        val normalized = FeatureNormalizer.normalize(vec)

        assertNotNull(normalized.normalized)
        normalized.normalized!!.forEachIndexed { i, v ->
            assertTrue("Feature $i value $v not in [0,1]", v in 0f..1f)
        }
    }

    @Test fun `normalize preserves raw features unchanged`() {
        val raw = floatArrayOf(500f, 500f, 500f, 500f, 50f, 50f, 50f, 50f, 50f, 0f, 0f, 0.5f, 0.3f, 0f)
        val vec = FeatureVector("u1", "2026-01-01", 4, raw)
        val norm = FeatureNormalizer.normalize(vec)
        assertArrayEquals(raw, norm.features, EPS)
    }

    // ── denormalizeAt() ──────────────────────────────────────────────────────

    @Test fun `denormalize 0 gives min bound`() {
        assertEquals(200f, FeatureNormalizer.denormalizeAt(0, 0f), EPS)
    }

    @Test fun `denormalize 1 gives max bound`() {
        assertEquals(5_000f, FeatureNormalizer.denormalizeAt(0, 1f), EPS)
    }

    @Test fun `denormalize 0_5 gives midpoint`() {
        assertEquals(2_600f, FeatureNormalizer.denormalizeAt(0, 0.5f), EPS)
    }

    @Test fun `normalize then denormalize is identity`() {
        val rawRt = 1_300f
        val normalized = FeatureNormalizer.normalizeAt(0, rawRt)
        val back       = FeatureNormalizer.denormalizeAt(0, normalized)
        assertEquals(rawRt, back, EPS)
    }

    // ── Bounds validation ────────────────────────────────────────────────────

    @Test fun `minBounds and maxBounds have correct size`() {
        assertEquals(FeatureVector.FEATURE_COUNT, FeatureNormalizer.minBounds.size)
        assertEquals(FeatureVector.FEATURE_COUNT, FeatureNormalizer.maxBounds.size)
    }

    @Test fun `every max is strictly greater than min`() {
        val mins = FeatureNormalizer.minBounds
        val maxs = FeatureNormalizer.maxBounds
        for (i in mins.indices) {
            assertTrue("Feature $i: max(${maxs[i]}) must be > min(${mins[i]})", maxs[i] > mins[i])
        }
    }
}
