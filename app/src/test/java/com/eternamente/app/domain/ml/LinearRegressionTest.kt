package com.eternamente.app.domain.ml

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for [LinearRegression] — pure Kotlin, no Android dependencies.
 */
class LinearRegressionTest {

    private val EPS = 0.001f   // acceptable floating-point delta

    // ── slope() ──────────────────────────────────────────────────────────────

    @Test fun `slope of empty list is 0`() {
        assertEquals(0f, LinearRegression.slope(emptyList()), EPS)
    }

    @Test fun `slope of single element is 0`() {
        assertEquals(0f, LinearRegression.slope(listOf(42f)), EPS)
    }

    @Test fun `slope of constant series is 0`() {
        assertEquals(0f, LinearRegression.slope(listOf(5f, 5f, 5f, 5f, 5f)), EPS)
    }

    @Test fun `slope of linear ascending series is 1`() {
        // y = [1, 2, 3, 4, 5] → slope = 1.0
        assertEquals(1f, LinearRegression.slope(listOf(1f, 2f, 3f, 4f, 5f)), EPS)
    }

    @Test fun `slope of linear descending series is -1`() {
        // y = [5, 4, 3, 2, 1] → slope = -1.0
        assertEquals(-1f, LinearRegression.slope(listOf(5f, 4f, 3f, 2f, 1f)), EPS)
    }

    @Test fun `slope of two-element ascending series is 10`() {
        // y = [0, 10] → Δy/Δx = 10
        assertEquals(10f, LinearRegression.slope(listOf(0f, 10f)), EPS)
    }

    @Test fun `slope with noise is approximately correct`() {
        // True slope ~2, with small perturbations
        val y = listOf(2f, 4.1f, 5.9f, 8.2f, 9.8f)  // near y = 2x + 2
        val s = LinearRegression.slope(y)
        assertTrue("Expected slope ~2, got $s", abs(s - 2f) < 0.3f)
    }

    @Test fun `slope of accuracy trend (improving scenario)`() {
        // Accuracy improving from 40 to 80 over 6 sessions
        val series = listOf(40f, 48f, 55f, 62f, 71f, 80f)
        val s = LinearRegression.slope(series)
        assertTrue("Expected positive slope, got $s", s > 0f)
    }

    @Test fun `slope of accuracy trend (declining scenario)`() {
        val series = listOf(80f, 75f, 68f, 61f, 50f, 42f)
        val s = LinearRegression.slope(series)
        assertTrue("Expected negative slope, got $s", s < 0f)
    }

    // ── meanAndStd() ──────────────────────────────────────────────────────────

    @Test fun `meanAndStd of empty list is 0,0`() {
        val (m, s) = LinearRegression.meanAndStd(emptyList())
        assertEquals(0f, m, EPS)
        assertEquals(0f, s, EPS)
    }

    @Test fun `meanAndStd of single element has zero std`() {
        val (m, s) = LinearRegression.meanAndStd(listOf(7f))
        assertEquals(7f, m, EPS)
        assertEquals(0f, s, EPS)
    }

    @Test fun `meanAndStd of uniform list has correct mean and zero std`() {
        val (m, s) = LinearRegression.meanAndStd(listOf(4f, 4f, 4f, 4f))
        assertEquals(4f, m, EPS)
        assertEquals(0f, s, EPS)
    }

    @Test fun `meanAndStd of known list`() {
        // [2, 4, 4, 4, 5, 5, 7, 9] → mean=5, std=2 (population)
        val (m, s) = LinearRegression.meanAndStd(listOf(2f, 4f, 4f, 4f, 5f, 5f, 7f, 9f))
        assertEquals(5f, m, EPS)
        assertEquals(2f, s, EPS)
    }

    // ── coefficientOfVariation() ──────────────────────────────────────────────

    @Test fun `cv of empty list is 0`() {
        assertEquals(0f, LinearRegression.coefficientOfVariation(emptyList()), EPS)
    }

    @Test fun `cv of uniform list is 0`() {
        assertEquals(0f, LinearRegression.coefficientOfVariation(listOf(500f, 500f, 500f)), EPS)
    }

    @Test fun `cv is std over mean`() {
        // mean=5, std=2 → cv=0.4
        val values = listOf(2f, 4f, 4f, 4f, 5f, 5f, 7f, 9f)
        assertEquals(0.4f, LinearRegression.coefficientOfVariation(values), EPS)
    }

    @Test fun `cv is zero when mean is zero`() {
        assertEquals(0f, LinearRegression.coefficientOfVariation(listOf(0f, 0f, 0f)), EPS)
    }

    // ── zScore() ─────────────────────────────────────────────────────────────

    @Test fun `zScore of same value as mean is 0`() {
        assertEquals(0f, LinearRegression.zScore(50f, 50f, 10f), EPS)
    }

    @Test fun `zScore one std above mean is 1`() {
        assertEquals(1f, LinearRegression.zScore(60f, 50f, 10f), EPS)
    }

    @Test fun `zScore is clipped at -3`() {
        // Extremely low value: z = -10 → clipped to -3
        assertEquals(-3f, LinearRegression.zScore(0f, 50f, 5f), EPS)
    }

    @Test fun `zScore is clipped at +3`() {
        assertEquals(3f, LinearRegression.zScore(100f, 50f, 5f), EPS)
    }

    @Test fun `zScore with zero std returns 0`() {
        assertEquals(0f, LinearRegression.zScore(80f, 50f, 0f), EPS)
    }
}
