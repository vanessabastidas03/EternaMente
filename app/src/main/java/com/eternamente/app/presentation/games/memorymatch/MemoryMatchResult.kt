package com.eternamente.app.presentation.games.memorymatch

import com.eternamente.app.presentation.games.engine.GameResult
import com.eternamente.app.presentation.games.engine.MetricsSnapshot

/**
 * Resultado del juego Memorama al finalizar (nivel motor).
 *
 * Contiene los datos específicos del juego además de las métricas genéricas.
 * [com.eternamente.app.presentation.games.memorymatch.MemoryMatchViewModel.buildDomainResult]
 * convierte este resultado al modelo de dominio para persistencia en Room.
 *
 * @property gameId              Identificador del juego (`"memory_match"`).
 * @property sessionId           UUID de la sesión.
 * @property metrics             Métricas de tiempo de reacción y precisión.
 * @property difficultyReached   Nivel alcanzado al finalizar.
 * @property totalPairs          Total de pares en el grid.
 * @property matchedPairs        Pares encontrados correctamente.
 * @property score               Puntuación bruta (pares×10 - errores×2).
 * @property timeRemainingSeconds Segundos restantes al terminar; 0 si fue timeout.
 * @property turnsCount          Número total de turnos (pares de cartas volteados).
 * @property completedByTimeout  `true` si el juego terminó por agotamiento del tiempo.
 */
data class MemoryMatchResult(
    override val gameId: String,
    override val sessionId: String,
    override val metrics: MetricsSnapshot,
    override val difficultyReached: Int,
    val totalPairs: Int,
    val matchedPairs: Int,
    val score: Int,
    val timeRemainingSeconds: Int,
    val turnsCount: Int,
    val completedByTimeout: Boolean
) : GameResult {

    /** Proporción de pares encontrados [0.0, 1.0]. */
    val completionRatio: Float
        get() = if (totalPairs > 0) matchedPairs.toFloat() / totalPairs else 0f

    /** `true` si el jugador encontró todos los pares antes de que se agotara el tiempo. */
    val isPerfectCompletion: Boolean
        get() = matchedPairs == totalPairs && !completedByTimeout
}
