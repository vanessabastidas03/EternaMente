package com.eternamente.app.presentation.games.engine

/**
 * Proveedor de tiempo falso para tests unitarios.
 *
 * Permite controlar con precisión los timestamps que usa [MetricsCollector]
 * y [GameTimer] sin depender de [android.os.SystemClock] (no disponible en JVM puro).
 *
 * Uso típico en tests:
 * ```kotlin
 * val fake = FakeTimeProvider()
 * val collector = MetricsCollector(timeProvider = fake, computeScope = UnconfinedTestDispatcher().scope)
 *
 * fake.nanos = 0L
 * collector.recordStimulusShown()
 * fake.nanos = 300_000_000L   // 300 ms en nanosegundos
 * collector.recordUserResponse(isCorrect = true)
 * ```
 *
 * @param nanos   Valor inicial de [elapsedRealtimeNanos]. Mutar directamente en tests.
 * @param millis  Valor inicial de [uptimeMillis]. Mutar directamente en tests.
 */
class FakeTimeProvider(
    var nanos: Long  = 0L,
    var millis: Long = 0L
) : TimeProvider {
    override fun elapsedRealtimeNanos(): Long = nanos
    override fun uptimeMillis(): Long          = millis
}
