package com.eternamente.app.presentation.games.engine

import android.os.SystemClock

/**
 * Abstracción del reloj del sistema para hacer [MetricsCollector] y [GameTimer] testables.
 *
 * - Producción: [SystemTimeProvider] — delega a [SystemClock].
 * - Tests: `FakeTimeProvider` (en el source set `test`) — devuelve valores inyectados.
 */
interface TimeProvider {
    /** Nanosegundos de reloj monotónico desde el arranque. No se ve afectado por cambios de zona horaria. */
    fun elapsedRealtimeNanos(): Long

    /** Milisegundos de reloj de uptime para el temporizador de juego. */
    fun uptimeMillis(): Long
}

/** Implementación de producción. */
class SystemTimeProvider : TimeProvider {
    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()
    override fun uptimeMillis(): Long          = SystemClock.uptimeMillis()
}
