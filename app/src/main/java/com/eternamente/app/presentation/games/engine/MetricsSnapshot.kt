package com.eternamente.app.presentation.games.engine

/**
 * Instantánea inmutable de las métricas acumuladas durante un juego cognitivo.
 *
 * Generada por [MetricsCollector.getMetrics] al finalizar el juego.
 * Estos valores alimentan directamente el modelo de dominio
 * [com.eternamente.app.domain.model.GameResult] que se persiste en Room.
 *
 * @property reactionTimes  Lista de tiempos de reacción (ms) en orden cronológico.
 * @property mean           Media aritmética de tiempos de reacción (ms).
 * @property median         Mediana (P50) de tiempos de reacción (ms).
 * @property p90            Percentil 90 de tiempos de reacción (ms).
 * @property accuracyPct    Porcentaje de respuestas correctas sobre el total [0, 100].
 * @property correctCount   Número de respuestas correctas.
 * @property errorCount     Número de respuestas incorrectas (comisiones).
 * @property omissionCount  Número de estímulos sin respuesta (omisiones).
 * @property totalTrials    Total de estímulos presentados.
 */
data class MetricsSnapshot(
    val reactionTimes: List<Float>,
    val mean: Float,
    val median: Float,
    val p90: Float,
    val accuracyPct: Float,
    val correctCount: Int,
    val errorCount: Int,
    val omissionCount: Int,
    val totalTrials: Int
) {
    companion object {
        /** Snapshot vacío usado como valor inicial antes de que termine el juego. */
        val EMPTY = MetricsSnapshot(
            reactionTimes = emptyList(),
            mean          = 0f,
            median        = 0f,
            p90           = 0f,
            accuracyPct   = 0f,
            correctCount  = 0,
            errorCount    = 0,
            omissionCount = 0,
            totalTrials   = 0
        )
    }
}
