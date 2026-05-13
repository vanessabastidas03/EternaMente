package com.eternamente.app.presentation.games.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Recopilador de métricas cognitivas para un juego en curso.
 *
 * ## Diseño de threading
 * | Método | Hilo | Justificación |
 * |---|---|---|
 * | [recordStimulusShown] | Cualquiera (UI) | Solo escribe un `AtomicLong` — nanosegundos |
 * | [recordUserResponse] | Cualquiera (UI) | Resta + división + insert en lista thread-safe — microsegundos |
 * | [getMetrics] | `Dispatchers.Default` | Ordena, calcula percentiles — decenas de µs, nunca en Main |
 *
 * La captura del timestamp debe hacerse **lo más cerca posible del evento** para
 * no introducir latencia artificial. Por eso [recordStimulusShown] y
 * [recordUserResponse] son funciones normales (no suspend) que ejecutan en
 * el hilo UI. El costo es trivial (<1µs).
 *
 * La computación estadística ([getMetrics]) se hace en [Dispatchers.Default]
 * para no bloquear el hilo principal si el motor la llama desde UI.
 *
 * @param timeProvider Proveedor del reloj. Sustituir por `FakeTimeProvider` en tests.
 */
class MetricsCollector(
    private val timeProvider: TimeProvider = SystemTimeProvider()
) {
    // ── Estado de estímulo pendiente ──────────────────────────────────────────
    // Long.MIN_VALUE = "sin estímulo pendiente".
    // No se usa 0L porque SystemClock puede devolver 0 en tests y confundirse con "sin estímulo".

    private val pendingStimulusNanos = AtomicLong(NO_STIMULUS)

    // ── Acumuladores thread-safe ──────────────────────────────────────────────

    private val trials    = CopyOnWriteArrayList<Trial>()
    private val omissions = AtomicInteger(0)

    // ── API de captura — llamadas desde UI thread ────────────────────────────

    /**
     * Registra el instante en que se presentó un estímulo.
     * Debe llamarse justo antes de que el estímulo sea visible en pantalla.
     */
    fun recordStimulusShown() {
        pendingStimulusNanos.set(timeProvider.elapsedRealtimeNanos())
    }

    /**
     * Registra la respuesta del usuario al estímulo actual.
     *
     * La captura del tiempo de respuesta ocurre en el hilo llamante (UI) para
     * minimizar el error de medición. La inserción en la lista es thread-safe.
     *
     * @param isCorrect `true` si la respuesta es cognitivamente correcta.
     */
    fun recordUserResponse(isCorrect: Boolean) {
        val responseNanos   = timeProvider.elapsedRealtimeNanos()
        val stimulusCapture = pendingStimulusNanos.getAndSet(NO_STIMULUS)

        if (stimulusCapture != NO_STIMULUS) {
            val rtMs = (responseNanos - stimulusCapture) / 1_000_000f
            trials.add(Trial(rtMs = rtMs, isCorrect = isCorrect))
        }
    }

    /**
     * Registra que el usuario no respondió a un estímulo dentro del tiempo límite.
     */
    fun recordOmission() {
        omissions.incrementAndGet()
        pendingStimulusNanos.set(NO_STIMULUS)
    }

    // ── Cálculo de métricas — en Dispatchers.Default ─────────────────────────

    /**
     * Calcula y devuelve la [MetricsSnapshot] con todos los ensayos acumulados.
     *
     * **Corre en [Dispatchers.Default]** — nunca bloquea el hilo principal.
     * El cálculo de percentiles con sort() puede tomar ~50–200µs para 100 ensayos.
     */
    suspend fun getMetrics(): MetricsSnapshot = withContext(Dispatchers.Default) {
        computeSnapshot(trials.toList(), omissions.get())
    }

    /** Reinicia todos los contadores. */
    fun reset() {
        trials.clear()
        omissions.set(0)
        pendingStimulusNanos.set(NO_STIMULUS)
    }

    private companion object {
        /** Valor centinela que indica que no hay estímulo pendiente. */
        const val NO_STIMULUS = Long.MIN_VALUE
    }

    // ── Cálculos internos ─────────────────────────────────────────────────────

    internal fun computeSnapshot(trials: List<Trial>, omitCount: Int): MetricsSnapshot {
        val correct   = trials.count { it.isCorrect }
        val incorrect = trials.count { !it.isCorrect }
        val total     = trials.size + omitCount
        val rts       = trials.map { it.rtMs }.sorted()

        val mean   = if (rts.isEmpty()) 0f else rts.average().toFloat()
        val median = rts.percentile(50)
        val p90    = rts.percentile(90)
        val acc    = if (total == 0) 0f else correct.toFloat() / total * 100f

        return MetricsSnapshot(
            reactionTimes = rts,
            mean          = mean,
            median        = median,
            p90           = p90,
            accuracyPct   = acc,
            correctCount  = correct,
            errorCount    = incorrect,
            omissionCount = omitCount,
            totalTrials   = total
        )
    }

    /**
     * Percentil con interpolación lineal (método C2 / Excel PERCENTILE.INC).
     *
     * Fórmula: `idx = (n - 1) * p / 100`
     * - Para n=4, p=50: idx = 1.5 → interpola entre sorted[1] y sorted[2] → 250 ✓
     * - Para n=3, p=50: idx = 1.0 → sorted[1] (elemento central) ✓
     * - Para n=10, p=90: idx = 8.1 → interpola entre sorted[8] y sorted[9] ✓
     */
    private fun List<Float>.percentile(pct: Int): Float {
        if (isEmpty()) return 0f
        if (size == 1) return this[0]
        val idx  = ((size - 1) * pct / 100.0).coerceIn(0.0, (size - 1).toDouble())
        val lo   = idx.toInt()
        val hi   = (lo + 1).coerceAtMost(size - 1)
        val frac = (idx - lo).toFloat()
        return this[lo] + frac * (this[hi] - this[lo])
    }

    internal data class Trial(val rtMs: Float, val isCorrect: Boolean)
}
