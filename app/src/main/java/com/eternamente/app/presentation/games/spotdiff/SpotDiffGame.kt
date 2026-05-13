package com.eternamente.app.presentation.games.spotdiff

import androidx.compose.ui.graphics.Color
import com.eternamente.app.presentation.games.engine.GameConfig
import com.eternamente.app.presentation.games.engine.GameEngine
import com.eternamente.app.presentation.games.engine.GameResult
import com.eternamente.app.presentation.games.engine.GameState
import com.eternamente.app.presentation.games.engine.GameTimer
import com.eternamente.app.presentation.games.engine.InputFeedback
import com.eternamente.app.presentation.games.engine.MetricsCollector
import com.eternamente.app.presentation.games.engine.MetricsSnapshot
import com.eternamente.app.presentation.games.engine.SystemTimeProvider
import com.eternamente.app.presentation.games.engine.UserInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// ── Modelos ───────────────────────────────────────────────────────────────────

/** Elemento visual de una cuadrícula 5×5. */
data class GridCell(val emoji: String, val row: Int, val col: Int)

/** Par de imágenes con diferencias definidas. */
data class DiffPair(
    val name: String,
    val leftGrid: List<List<String>>,   // 5 rows × 5 cols
    val rightGrid: List<List<String>>,  // mismo pero con diferencias
    val diffPositions: Set<Pair<Int,Int>>  // (row, col) de diferencias en la derecha
)

/** Diferencias predefinidas: pares de escenas con variaciones. */
val DIFF_PAIRS = listOf(
    DiffPair("Granja", listOf(
        listOf("🌳","🌳","🏡","🌳","🌳"),listOf("🐔","🐔","🐄","🐂","🐔"),
        listOf("🌾","🌾","🌾","🌾","🌾"),listOf("🌻","🌻","🌻","🌻","🌻"),
        listOf("🐕","🐕","🚜","🐕","🐕")
    ), listOf(
        listOf("🌳","🌲","🏡","🌳","🌳"),listOf("🐔","🐔","🐄","🐑","🐔"),
        listOf("🌾","🌿","🌾","🌾","🌾"),listOf("🌻","🌻","🌷","🌻","🌻"),
        listOf("🐕","🐕","🚜","🐕","🐈")
    ), setOf(1 to 0 to 1, 0 to 1 to 3, 2 to 1 to 1).map { it.first.first to it.first.second }.toSet()),
    DiffPair("Ciudad", listOf(
        listOf("🏢","🏢","🏦","🏢","🏢"),listOf("🚗","🚕","🚌","🚗","🚕"),
        listOf("🌳","🌳","⛲","🌳","🌳"),listOf("👩","👨","👧","👦","👩"),
        listOf("🏪","🏪","🍕","🏪","🏪")
    ), listOf(
        listOf("🏢","🏗️","🏦","🏢","🏢"),listOf("🚗","🚕","🚌","🚙","🚕"),
        listOf("🌳","🌴","⛲","🌳","🌳"),listOf("👩","👨","👧","👦","👴"),
        listOf("🏪","🏪","🍔","🏪","🏪")
    ), setOf(0 to 1, 1 to 3, 2 to 1, 3 to 4, 4 to 2))
)

data class SpotDiffConfig(
    override val gameId: String = GAME_ID,
    override val sessionId: String, override val userId: String,
    override val difficultyLevel: Int = 1,
    val timeLimitSeconds: Int = 180,
    val requiredDifferences: Int = 5
) : GameConfig {
    companion object {
        const val GAME_ID = "spot_diff"
        fun forDifficulty(level: Int, sessionId: String, userId: String) = SpotDiffConfig(
            sessionId=sessionId, userId=userId, difficultyLevel=level,
            timeLimitSeconds = when(level) {1->180;2->150;3->120;4->90;else->60},
            requiredDifferences = if (level <= 2) 4 else 5
        )
    }
}

data class SpotDiffResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val foundCount: Int, val totalDifferences: Int,
    val falseTaps: Int, val timeElapsed: Int
) : GameResult

data class SpotDiffUiState(
    val pair: DiffPair? = null,
    val foundPositions: Set<Pair<Int,Int>> = emptySet(),
    val lastTapCorrect: Boolean? = null,
    val falseTaps: Int = 0,
    val timeLeft: Int = 180
)

// ── Engine ────────────────────────────────────────────────────────────────────

class SpotDiffEngine(override val config: SpotDiffConfig) :
    GameEngine<SpotDiffConfig, SpotDiffResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())

    private val _uiState = MutableStateFlow(SpotDiffUiState(timeLeft = config.timeLimitSeconds))
    val uiState: StateFlow<SpotDiffUiState> = _uiState.asStateFlow()

    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private var timeLeft = config.timeLimitSeconds
    private val pair     = DIFF_PAIRS.random()
    private var foundPositions = mutableSetOf<Pair<Int,Int>>()
    private var falseTaps = 0; private var startMillis = 0L
    private var finalResult: SpotDiffResult? = null

    private val timer = GameTimer(config.timeLimitSeconds, SystemTimeProvider(), scope,
        onTick     = { left -> timeLeft = left; _uiState.value = _uiState.value.copy(timeLeft = left); _state.value = GameState.Playing(foundPositions.size.toFloat() / pair.diffPositions.size, left) },
        onComplete = { scope.launch { endGame() } }
    )

    override fun start() { _state.value = GameState.Instructions }

    fun startCountdown() {
        scope.launch {
            for (i in 3 downTo 1) { _state.value = GameState.Countdown(i); delay(1000L) }
            startMillis = SystemTimeProvider().uptimeMillis()
            _uiState.value = _uiState.value.copy(pair = pair, timeLeft = config.timeLimitSeconds)
            _state.value = GameState.Playing(0f, config.timeLimitSeconds)
            metrics.recordStimulusShown()
            timer.start()
        }
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing) return InputFeedback.Ignored
        if (input !is UserInput.TapTarget) return InputFeedback.Ignored
        // targetId = "row,col"
        val parts = input.targetId.split(",")
        val row = parts.getOrNull(0)?.toIntOrNull() ?: return InputFeedback.Ignored
        val col = parts.getOrNull(1)?.toIntOrNull() ?: return InputFeedback.Ignored
        val pos = row to col

        return if (pos in pair.diffPositions && pos !in foundPositions) {
            foundPositions.add(pos)
            metrics.recordUserResponse(isCorrect = true)
            _uiState.value = _uiState.value.copy(foundPositions = foundPositions.toSet(), lastTapCorrect = true)
            _state.value = GameState.Playing(foundPositions.size.toFloat() / pair.diffPositions.size, timeLeft)
            if (foundPositions.size >= pair.diffPositions.size) scope.launch { endGame() }
            InputFeedback.Correct
        } else {
            falseTaps++
            metrics.recordUserResponse(isCorrect = false)
            _uiState.value = _uiState.value.copy(lastTapCorrect = false, falseTaps = falseTaps)
            InputFeedback.Incorrect
        }
    }

    private suspend fun endGame() {
        timer.cancel()
        val elapsed = ((SystemTimeProvider().uptimeMillis() - startMillis) / 1000).toInt()
        val snap    = metrics.getMetrics()
        finalResult = SpotDiffResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel,
            foundCount=foundPositions.size, totalDifferences=pair.diffPositions.size,
            falseTaps=falseTaps, timeElapsed=elapsed
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause()  { timer.pause();  _state.value = GameState.Paused }
    override fun resume() { timer.resume(); _state.value = GameState.Playing(foundPositions.size.toFloat() / pair.diffPositions.size, timeLeft) }
    override fun forceEnd(): SpotDiffResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
