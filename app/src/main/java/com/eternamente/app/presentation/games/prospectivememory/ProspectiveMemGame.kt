package com.eternamente.app.presentation.games.prospectivememory

import com.eternamente.app.presentation.games.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

data class ProspectiveConfig(
    override val gameId: String = GAME_ID, override val sessionId: String,
    override val userId: String, override val difficultyLevel: Int = 1,
    val targetEmoji: String = "🐦",
    val distractorEmojis: List<String> = listOf("🌸","🍀","🌙","⭐","🔵","🔴","🟢"),
    val totalDistractors: Int = 20,
    val targetAppearances: Int = 4,
    val responseWindowMs: Long = 2_500L
) : GameConfig {
    companion object {
        const val GAME_ID = "prospective_memory"
        fun forDifficulty(level: Int, sessionId: String, userId: String) = ProspectiveConfig(
            sessionId=sessionId, userId=userId, difficultyLevel=level,
            targetAppearances = if (level <= 2) 3 else 4,
            responseWindowMs = when(level){1->3000L;2->2500L;3->2000L;4->1800L;else->1500L}
        )
    }
}

data class ProspectiveResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val hits: Int, val misses: Int, val falsePositives: Int, val targetAppearances: Int
) : GameResult

enum class ProspectivePhase { SETUP, DISTRACTION }

data class ProspectiveUiState(
    val phase: ProspectivePhase      = ProspectivePhase.SETUP,
    val targetEmoji: String          = "🐦",
    val currentEmoji: String?        = null,
    val isTargetVisible: Boolean     = false,
    val circleActive: Boolean        = false,
    val hits: Int = 0, val misses: Int = 0, val falsePositives: Int = 0,
    val stimulusIndex: Int = 0, val totalStimuli: Int = 24
)

class ProspectiveMemEngine(override val config: ProspectiveConfig) :
    GameEngine<ProspectiveConfig, ProspectiveResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())
    private val _uiState = MutableStateFlow(ProspectiveUiState(targetEmoji = config.targetEmoji))
    val uiState: StateFlow<ProspectiveUiState> = _uiState.asStateFlow()
    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private var hits = 0; private var misses = 0; private var falsePositives = 0
    private var isTargetShowing = false; private var responseWindowOpen = false
    private var finalResult: ProspectiveResult? = null

    override fun start() { _state.value = GameState.Instructions }

    fun begin() { scope.launch { runDistractionTask() } }

    private suspend fun runDistractionTask() {
        _uiState.value = _uiState.value.copy(phase = ProspectivePhase.DISTRACTION)
        _state.value   = GameState.Playing(0f, null)

        // Construir secuencia: target en posiciones random
        val total     = config.totalDistractors + config.targetAppearances
        val targetPos = (0 until total).shuffled().take(config.targetAppearances).toSet()

        for (i in 0 until total) {
            val isTarget = i in targetPos
            val emoji    = if (isTarget) config.targetEmoji else config.distractorEmojis.random()
            isTargetShowing  = isTarget
            responseWindowOpen = isTarget

            if (isTarget) metrics.recordStimulusShown()
            _uiState.value = _uiState.value.copy(
                currentEmoji = emoji, isTargetVisible = isTarget,
                circleActive = isTarget, stimulusIndex = i, totalStimuli = total
            )

            delay(config.responseWindowMs)
            // Si era target y no respondieron → miss
            if (isTarget && responseWindowOpen) { misses++; metrics.recordOmission() }
            responseWindowOpen = false; isTargetShowing = false
            _uiState.value = _uiState.value.copy(currentEmoji = null, isTargetVisible = false, circleActive = false)
            delay(300L)

            _state.value = GameState.Playing(i.toFloat() / total, null)
            _uiState.value = _uiState.value.copy(hits = hits, misses = misses, falsePositives = falsePositives)
        }
        endGame()
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing || input !is UserInput.Tap) return InputFeedback.Ignored
        return if (responseWindowOpen && isTargetShowing) {
            hits++; responseWindowOpen = false; metrics.recordUserResponse(true)
            _uiState.value = _uiState.value.copy(hits = hits)
            InputFeedback.Correct
        } else {
            falsePositives++; metrics.recordUserResponse(false)
            _uiState.value = _uiState.value.copy(falsePositives = falsePositives)
            InputFeedback.Incorrect
        }
    }

    private suspend fun endGame() {
        val snap = metrics.getMetrics()
        finalResult = ProspectiveResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel, hits=hits, misses=misses,
            falsePositives=falsePositives, targetAppearances=config.targetAppearances
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause() {}; override fun resume() {}
    override fun forceEnd(): ProspectiveResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
