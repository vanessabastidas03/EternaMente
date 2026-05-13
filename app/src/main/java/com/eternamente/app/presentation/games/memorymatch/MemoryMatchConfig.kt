package com.eternamente.app.presentation.games.memorymatch

import com.eternamente.app.presentation.games.engine.GameConfig

/**
 * Configuración del juego Memorama de Pares.
 *
 * @property gameId          Identificador estable del juego.
 * @property sessionId       UUID de la sesión cognitiva activa.
 * @property userId          UUID del usuario que juega.
 * @property difficultyLevel Nivel 1–5.
 * @property columns         Número de columnas del grid.
 * @property rows            Número de filas del grid.
 * @property cardSet         Conjunto de símbolos para las tarjetas.
 * @property timeLimitSeconds Tiempo límite en segundos.
 */
data class MemoryMatchConfig(
    override val gameId: String           = GAME_ID,
    override val sessionId: String,
    override val userId: String,
    override val difficultyLevel: Int     = 1,
    val columns: Int                      = 4,
    val rows: Int                         = 4,
    val cardSet: CardSet                  = CardSet.ANIMALS,
    val timeLimitSeconds: Int             = 90
) : GameConfig {

    /** Número total de tarjetas en el grid. */
    val totalCards: Int get() = columns * rows

    /** Número de pares únicos requeridos. */
    val totalPairs: Int get() = totalCards / 2

    companion object {
        const val GAME_ID = "memory_match"

        /**
         * Crea la configuración apropiada para cada nivel de dificultad.
         *
         * | Nivel | Grid  | Pares | Símbolos   | Tiempo |
         * |-------|-------|-------|------------|--------|
         * | 1     | 4 × 4 | 8     | Animales   | 90 s   |
         * | 2     | 4 × 4 | 8     | Animales   | 75 s   |
         * | 3     | 4 × 6 | 12    | Geométrico | 60 s   |
         * | 4     | 5 × 6 | 15    | Geométrico | 50 s   |
         * | 5     | 6 × 6 | 18    | Símbolos   | 45 s   |
         */
        fun forDifficulty(level: Int, sessionId: String, userId: String): MemoryMatchConfig =
            when (level.coerceIn(1, 5)) {
                1 -> MemoryMatchConfig(
                    sessionId = sessionId, userId = userId, difficultyLevel = 1,
                    columns = 4, rows = 4, cardSet = CardSet.ANIMALS, timeLimitSeconds = 90
                )
                2 -> MemoryMatchConfig(
                    sessionId = sessionId, userId = userId, difficultyLevel = 2,
                    columns = 4, rows = 4, cardSet = CardSet.ANIMALS, timeLimitSeconds = 75
                )
                3 -> MemoryMatchConfig(
                    sessionId = sessionId, userId = userId, difficultyLevel = 3,
                    columns = 4, rows = 6, cardSet = CardSet.GEOMETRIC, timeLimitSeconds = 60
                )
                4 -> MemoryMatchConfig(
                    sessionId = sessionId, userId = userId, difficultyLevel = 4,
                    columns = 5, rows = 6, cardSet = CardSet.GEOMETRIC, timeLimitSeconds = 50
                )
                else -> MemoryMatchConfig(
                    sessionId = sessionId, userId = userId, difficultyLevel = 5,
                    columns = 6, rows = 6, cardSet = CardSet.SYMBOLS, timeLimitSeconds = 45
                )
            }
    }
}
