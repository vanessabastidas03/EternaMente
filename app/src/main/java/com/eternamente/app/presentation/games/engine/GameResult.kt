package com.eternamente.app.presentation.games.engine

/**
 * Contrato del resultado de un juego cognitivo — nivel de motor.
 *
 * **Distinto del modelo de dominio** [com.eternamente.app.domain.model.GameResult]:
 * esta interfaz vive en la capa de presentación y lleva los datos RAW del motor.
 * [GameBaseViewModel.buildDomainResult] la convierte al modelo de dominio para
 * persistencia en Room.
 *
 * @property gameId          Identificador del mini-juego.
 * @property sessionId       UUID de la sesión activa.
 * @property metrics         Métricas calculadas por [MetricsCollector].
 * @property difficultyReached Nivel de dificultad alcanzado al finalizar.
 */
interface GameResult {
    val gameId: String
    val sessionId: String
    val metrics: MetricsSnapshot
    val difficultyReached: Int
}
