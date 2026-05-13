package com.eternamente.app.presentation.games.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [MetricsCollector].
 *
 * ## Estrategia de testing
 * - [MetricsCollector.recordStimulusShown] y [recordUserResponse] son funciones
 *   normales (síncrona) → se llaman directamente en el test sin coroutinas.
 * - [MetricsCollector.getMetrics] es `suspend` y corre en `Dispatchers.Default`.
 *   Los tests usan `runTest { }` que proporciona un dispatcher de test que controla
 *   la ejecución de todas las corutinas hijas.
 * - [FakeTimeProvider] permite controlar timestamps con precisión de nanosegundo.
 */
class MetricsCollectorTest {

    private val fakeTime = FakeTimeProvider()
    private lateinit var collector: MetricsCollector

    @Before
    fun setUp() {
        collector = MetricsCollector(timeProvider = fakeTime)
    }

    // ── Tiempo de reacción ────────────────────────────────────────────────────

    @Test
    fun `tiempo de reaccion es calculado correctamente`() = runTest {
        fakeTime.nanos = 0L
        collector.recordStimulusShown()

        fakeTime.nanos = 300_000_000L          // 300 ms en nanos
        collector.recordUserResponse(isCorrect = true)

        val metrics = collector.getMetrics()
        assertEquals(300f, metrics.mean, 1f)
        assertEquals(300f, metrics.reactionTimes.first(), 1f)
        assertEquals(1, metrics.totalTrials)
    }

    @Test
    fun `multiples RTs producen media correcta`() = runTest {
        recordResponse(rtMs = 100f, isCorrect = true)
        recordResponse(rtMs = 200f, isCorrect = true)
        recordResponse(rtMs = 300f, isCorrect = true)
        recordResponse(rtMs = 400f, isCorrect = true)

        val metrics = collector.getMetrics()
        assertEquals(250f, metrics.mean, 1f)
        assertEquals(4,    metrics.totalTrials)
    }

    @Test
    fun `mediana es correcta con numero par de ensayos`() = runTest {
        // RTs ordenados: 100, 200, 300, 400 → mediana interpolada = 250
        recordResponse(rtMs = 400f, isCorrect = false)
        recordResponse(rtMs = 100f, isCorrect = true)
        recordResponse(rtMs = 300f, isCorrect = false)
        recordResponse(rtMs = 200f, isCorrect = true)

        assertEquals(250f, collector.getMetrics().median, 2f)
    }

    @Test
    fun `mediana es correcta con numero impar de ensayos`() = runTest {
        recordResponse(rtMs = 300f, isCorrect = true)
        recordResponse(rtMs = 100f, isCorrect = true)
        recordResponse(rtMs = 200f, isCorrect = true)

        assertEquals(200f, collector.getMetrics().median, 2f)
    }

    @Test
    fun `p90 es calculado dentro del rango esperado`() = runTest {
        (1..10).forEach { i ->
            recordResponse(rtMs = (i * 10).toFloat(), isCorrect = true)
        }

        val p90 = collector.getMetrics().p90
        assertTrue("P90 debe ser >= 80ms, fue $p90", p90 >= 80f)
        assertTrue("P90 debe ser <= 100ms, fue $p90", p90 <= 100f)
    }

    // ── Precisión ─────────────────────────────────────────────────────────────

    @Test
    fun `accuracyPct con 3 correctas y 1 incorrecta`() = runTest {
        repeat(3) { recordResponse(isCorrect = true) }
        recordResponse(isCorrect = false)

        val m = collector.getMetrics()
        assertEquals(75f, m.accuracyPct, 0.1f)
        assertEquals(3,   m.correctCount)
        assertEquals(1,   m.errorCount)
        assertEquals(4,   m.totalTrials)
    }

    @Test
    fun `accuracy es 100 pct cuando todas correctas`() = runTest {
        repeat(5) { recordResponse(isCorrect = true) }
        assertEquals(100f, collector.getMetrics().accuracyPct, 0.01f)
    }

    @Test
    fun `accuracy es 0 pct cuando ninguna correcta`() = runTest {
        repeat(4) { recordResponse(isCorrect = false) }
        assertEquals(0f, collector.getMetrics().accuracyPct, 0.01f)
    }

    // ── Omisiones ─────────────────────────────────────────────────────────────

    @Test
    fun `omisiones se registran correctamente`() = runTest {
        repeat(2) { recordResponse(isCorrect = true) }
        collector.recordOmission()
        collector.recordOmission()

        val m = collector.getMetrics()
        assertEquals(2,   m.omissionCount)
        assertEquals(4,   m.totalTrials)         // 2 respuestas + 2 omisiones
        assertEquals(50f, m.accuracyPct, 0.1f)
    }

    // ── Estado vacío ──────────────────────────────────────────────────────────

    @Test
    fun `snapshot vacio cuando no hay ensayos`() = runTest {
        val m = collector.getMetrics()

        assertTrue(m.reactionTimes.isEmpty())
        assertEquals(0f, m.mean,        0.01f)
        assertEquals(0f, m.median,      0.01f)
        assertEquals(0f, m.p90,         0.01f)
        assertEquals(0f, m.accuracyPct, 0.01f)
        assertEquals(0,  m.totalTrials)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset limpia ensayos y omisiones`() = runTest {
        repeat(3) { recordResponse(isCorrect = true) }
        collector.recordOmission()

        collector.reset()

        val m = collector.getMetrics()
        assertTrue(m.reactionTimes.isEmpty())
        assertEquals(0, m.totalTrials)
        assertEquals(0, m.omissionCount)
    }

    // ── Sin estímulo previo ───────────────────────────────────────────────────

    @Test
    fun `respuesta sin stimulus previo no genera trial`() = runTest {
        collector.recordUserResponse(isCorrect = true)  // sin stimulus → no trial
        assertEquals(0, collector.getMetrics().totalTrials)
    }

    @Test
    fun `segunda respuesta al mismo stimulus no genera trial extra`() = runTest {
        fakeTime.nanos = 0L
        collector.recordStimulusShown()

        fakeTime.nanos = 200_000_000L
        collector.recordUserResponse(isCorrect = true)   // consume pendingStimulusNanos

        fakeTime.nanos = 300_000_000L
        collector.recordUserResponse(isCorrect = false)  // sin estímulo pendiente → ignorado

        assertEquals(1, collector.getMetrics().totalTrials)
    }

    // ── computeSnapshot interno ────────────────────────────────────────────────

    @Test
    fun `computeSnapshot calcula correctamente con datos conocidos`() {
        val trials = listOf(
            MetricsCollector.Trial(rtMs = 100f, isCorrect = true),
            MetricsCollector.Trial(rtMs = 200f, isCorrect = true),
            MetricsCollector.Trial(rtMs = 300f, isCorrect = false)
        )

        val snapshot = collector.computeSnapshot(trials, omitCount = 1)

        assertEquals(200f, snapshot.mean, 1f)
        assertEquals(2,    snapshot.correctCount)
        assertEquals(1,    snapshot.errorCount)
        assertEquals(1,    snapshot.omissionCount)
        assertEquals(4,    snapshot.totalTrials)   // 3 respuestas + 1 omisión
        assertEquals(50f,  snapshot.accuracyPct, 0.1f)  // 2 correctas de 4
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun recordResponse(isCorrect: Boolean, rtMs: Float = 250f) {
        fakeTime.nanos = 0L
        collector.recordStimulusShown()
        fakeTime.nanos = (rtMs * 1_000_000f).toLong()
        collector.recordUserResponse(isCorrect = isCorrect)
    }
}
