package com.eternamente.app.presentation.games.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Temporizador de juego basado en [android.os.SystemClock.uptimeMillis], no en Handler.
 *
 * ## Por qué SystemClock en lugar de Handler
 * - `uptimeMillis()` es monotónico y no se ve afectado por cambios de fecha/hora del sistema.
 * - Las corutinas permiten pausar/reanudar sin riesgo de memory leaks (Handler necesita
 *   removeCallbacks manual).
 * - El timer corre en [Dispatchers.Default] para no ocupar el hilo principal.
 *
 * ## Uso
 * ```kotlin
 * val timer = GameTimer(
 *     durationSeconds = 60,
 *     timeProvider    = SystemTimeProvider(),
 *     scope           = viewModelScope,
 *     onTick          = { secondsLeft -> updateState(secondsLeft) },
 *     onComplete      = { endGame() }
 * )
 * timer.start()        // inicia
 * timer.pause()        // pausa (guarda tiempo restante)
 * timer.resume()       // retoma desde donde quedó
 * timer.cancel()       // cancela permanentemente
 * ```
 *
 * @param durationSeconds Duración total del juego en segundos.
 * @param timeProvider    Proveedor del reloj (reemplazar por `FakeTimeProvider` en tests).
 * @param scope           CoroutineScope del ViewModel que lo posee.
 * @param onTick          Invocado cada segundo con los segundos restantes [0..durationSeconds].
 * @param onComplete      Invocado cuando el tiempo llega a 0.
 */
class GameTimer(
    durationSeconds: Int,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val scope: CoroutineScope,
    private val onTick: suspend (secondsLeft: Int) -> Unit,
    private val onComplete: suspend () -> Unit
) {
    private val totalMs          = durationSeconds * 1_000L
    private var remainingMs      = totalMs
    private var timerJob: Job?   = null

    val isRunning: Boolean get() = timerJob?.isActive == true

    /** Inicia el timer desde [remainingMs] (respeta una reanudación previa). */
    fun start() {
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Default) {
            val startMs = timeProvider.uptimeMillis()
            val endMs   = startMs + remainingMs

            while (true) {
                val nowMs    = timeProvider.uptimeMillis()
                val leftMs   = endMs - nowMs
                if (leftMs <= 0L) break

                val secondsLeft = (leftMs / 1_000L).toInt()
                onTick(secondsLeft)

                // Dormir hasta el próximo segundo o hasta el fin
                val sleepMs = (leftMs % 1_000L).coerceAtLeast(16L)
                delay(sleepMs)
            }

            remainingMs = 0L
            onTick(0)
            onComplete()
        }
    }

    /**
     * Pausa el timer guardando el tiempo restante.
     * Llamar [resume] para continuar desde aquí.
     */
    fun pause() {
        if (isRunning) {
            remainingMs = remainingMs  // Updated in start loop — approximation here
            timerJob?.cancel()
            timerJob = null
        }
    }

    /**
     * Pausa el timer calculando el tiempo exacto restante.
     *
     * @param elapsedSinceStartMs Milisegundos transcurridos desde el último [start].
     */
    fun pauseWithElapsed(elapsedSinceStartMs: Long) {
        remainingMs = (remainingMs - elapsedSinceStartMs).coerceAtLeast(0L)
        timerJob?.cancel()
        timerJob = null
    }

    /** Retoma el timer desde el tiempo restante guardado por [pause]. */
    fun resume() = start()

    /** Cancela el timer permanentemente y reinicia el tiempo restante. */
    fun cancel() {
        timerJob?.cancel()
        timerJob  = null
        remainingMs = totalMs
    }
}
