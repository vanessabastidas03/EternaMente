package com.eternamente.app.presentation.games.flashcolor

import androidx.compose.ui.graphics.Color
import com.eternamente.app.presentation.games.engine.GameConfig
import com.eternamente.app.presentation.games.engine.GameResult
import com.eternamente.app.presentation.games.engine.MetricsSnapshot

enum class StimulusColor(val displayName: String, val color: Color) {
    RED("Rojo",     Color(0xFFE53935)),
    BLUE("Azul",    Color(0xFF1E88E5)),
    GREEN("Verde",  Color(0xFF43A047)),
    YELLOW("Amarillo", Color(0xFFFDD835)),
    ORANGE("Naranja",  Color(0xFFEF6C00)),
    PURPLE("Morado",   Color(0xFF8E24AA))
}

data class FlashColorConfig(
    override val gameId: String       = GAME_ID,
    override val sessionId: String,
    override val userId: String,
    override val difficultyLevel: Int = 1,
    val targetColor: StimulusColor    = StimulusColor.RED,
    val totalStimuli: Int             = 30,
    val stimulusDurationMs: Long      = 400L,
    val isiDurationMs: Long           = 200L
) : GameConfig {
    companion object {
        const val GAME_ID = "flash_color"
        fun forDifficulty(level: Int, sessionId: String, userId: String): FlashColorConfig {
            val duration = when (level) { 1 -> 500L; 2 -> 400L; 3 -> 350L; 4 -> 300L; else -> 250L }
            val targets  = StimulusColor.entries.random()
            return FlashColorConfig(sessionId=sessionId, userId=userId, difficultyLevel=level, targetColor=targets, stimulusDurationMs=duration)
        }
    }
}

data class FlashColorResult(
    override val gameId: String,
    override val sessionId: String,
    override val metrics: MetricsSnapshot,
    override val difficultyReached: Int,
    val targetColor: StimulusColor,
    val hits: Int, val misses: Int, val falseAlarms: Int, val correctRejections: Int,
    val dPrime: Float, val hitRate: Float, val faRate: Float
) : GameResult

data class FlashColorUiState(
    val currentColor: StimulusColor? = null,
    val targetColor: StimulusColor   = StimulusColor.RED,
    val stimulusIndex: Int           = 0,
    val totalStimuli: Int            = 30,
    val hits: Int = 0, val misses: Int = 0, val falseAlarms: Int = 0,
    val isShowingStimulus: Boolean   = false
)
