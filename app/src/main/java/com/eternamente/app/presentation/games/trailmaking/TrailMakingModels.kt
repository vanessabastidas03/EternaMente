package com.eternamente.app.presentation.games.trailmaking

import androidx.compose.ui.geometry.Offset
import com.eternamente.app.presentation.games.engine.GameConfig
import com.eternamente.app.presentation.games.engine.GameResult
import com.eternamente.app.presentation.games.engine.MetricsSnapshot

data class TrailMakingConfig(
    override val gameId: String       = GAME_ID,
    override val sessionId: String,
    override val userId: String,
    override val difficultyLevel: Int = 1,
    val nodeCount: Int                = 25,
    val isAlternating: Boolean        = false,  // nivel 3+ = números Y letras
    val timeLimitSeconds: Int         = 120
) : GameConfig {
    companion object {
        const val GAME_ID = "trail_making"
        fun forDifficulty(level: Int, sessionId: String, userId: String) = TrailMakingConfig(
            sessionId=sessionId, userId=userId, difficultyLevel=level,
            nodeCount    = if (level <= 2) 15 else 25,
            isAlternating = level >= 3,
            timeLimitSeconds = when(level) { 1->180; 2->150; 3->120; 4->90; else->60 }
        )
    }
}

data class TrailNode(
    val label: String,        // "1"…"25" o "1","A","2","B"…
    val position: Offset,     // normalizado 0..1
    val isConnected: Boolean = false,
    val isTarget: Boolean    = false
)

data class TrailMakingResult(
    override val gameId: String,
    override val sessionId: String,
    override val metrics: MetricsSnapshot,
    override val difficultyReached: Int,
    val completedNodes: Int,
    val totalNodes: Int,
    val sequenceErrors: Int,
    val timeElapsedSeconds: Int,
    val completedSuccessfully: Boolean
) : GameResult

data class TrailMakingUiState(
    val nodes: List<TrailNode>     = emptyList(),
    val connectedPath: List<Int>   = emptyList(),
    val currentTarget: Int         = 0,
    val sequenceErrors: Int        = 0,
    val timeLeft: Int              = 120,
    val touchPath: List<Offset>    = emptyList()
)
