package com.eternamente.app.presentation.games.engine

/**
 * Contrato de configuración para todos los juegos cognitivos.
 *
 * Cada juego implementa su propia clase de configuración que extiende esta interfaz
 * añadiendo parámetros específicos (ej. `StroopConfig`, `DigitSpanConfig`).
 *
 * @property gameId        Identificador estable del mini-juego (ej. `"stroop"`, `"digit_span"`).
 * @property sessionId     UUID de la [com.eternamente.app.domain.model.CognitiveSession] activa.
 * @property userId        UUID del usuario que juega (para guardar resultados).
 * @property difficultyLevel Nivel inicial de dificultad (1–5). Ver [DifficultyManager].
 */
interface GameConfig {
    val gameId: String
    val sessionId: String
    val userId: String
    val difficultyLevel: Int
}
