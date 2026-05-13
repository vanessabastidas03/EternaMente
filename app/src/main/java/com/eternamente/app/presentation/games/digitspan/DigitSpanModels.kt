package com.eternamente.app.presentation.games.digitspan

import com.eternamente.app.presentation.games.engine.GameConfig
import com.eternamente.app.presentation.games.engine.GameResult
import com.eternamente.app.presentation.games.engine.MetricsSnapshot

// ══════════════════════════════════════════════════════════════════════════════
// Configuración
// ══════════════════════════════════════════════════════════════════════════════

data class DigitSpanConfig(
    override val gameId: String       = GAME_ID,
    override val sessionId: String,
    override val userId: String,
    override val difficultyLevel: Int = 1,
    val sequenceLength: Int           = 3,   // 3 (L1) → 7 (L5)
    val isBackward: Boolean           = false // true desde nivel 3
) : GameConfig {

    companion object {
        const val GAME_ID = "digit_span"

        fun forDifficulty(level: Int, sessionId: String, userId: String) =
            DigitSpanConfig(
                sessionId     = sessionId,
                userId        = userId,
                difficultyLevel = level,
                sequenceLength  = (level + 2).coerceIn(3, 7),
                isBackward      = level >= 3
            )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Resultado (nivel motor)
// ══════════════════════════════════════════════════════════════════════════════

data class DigitSpanResult(
    override val gameId: String,
    override val sessionId: String,
    override val metrics: MetricsSnapshot,
    override val difficultyReached: Int,
    val sequenceLength: Int,
    val maxCorrectSpan: Int,       // longitud máxima correctamente recordada
    val positionErrors: List<Int>, // índices donde se cometió error
    val isBackward: Boolean,
    val totalRounds: Int,
    val correctRounds: Int
) : GameResult

// ══════════════════════════════════════════════════════════════════════════════
// Estado adicional del juego (fuera de GameState)
// ══════════════════════════════════════════════════════════════════════════════

enum class SpanPhase { SHOWING, INPUT, FEEDBACK }

data class DigitSpanUiState(
    val phase: SpanPhase          = SpanPhase.SHOWING,
    val displayedDigit: Int?      = null,   // null = intervalo entre dígitos
    val userInput: List<Int>      = emptyList(),
    val sequenceLength: Int       = 3,
    val isBackward: Boolean       = false,
    val lastWasCorrect: Boolean?  = null,   // feedback breve tras intento
    val roundIndex: Int           = 0
)
