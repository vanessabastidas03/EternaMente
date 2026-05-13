package com.eternamente.app.presentation.games.corsiblock

import com.eternamente.app.presentation.games.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/** Posición normalizada de un bloque en pantalla. */
data class BlockPosition(val x: Float, val y: Float)

data class CorsiConfig(
    override val gameId: String = GAME_ID, override val sessionId: String,
    override val userId: String, override val difficultyLevel: Int = 1,
    val sequenceLength: Int = 2, val totalRounds: Int = 5
) : GameConfig {
    companion object {
        const val GAME_ID = "corsi_block"
        fun forDifficulty(level: Int, sessionId: String, userId: String) = CorsiConfig(
            sessionId=sessionId, userId=userId, difficultyLevel=level,
            sequenceLength = (level + 1).coerceIn(2, 6)
        )
    }
}

data class CorsiResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val maxCorrectSpan: Int, val correctRounds: Int, val totalRounds: Int
) : GameResult

enum class CorsiPhase { SHOWING, INPUT, FEEDBACK }

data class CorsiUiState(
    val blockPositions: List<BlockPosition> = BLOCK_POSITIONS,
    val highlightedBlock: Int? = null,
    val phase: CorsiPhase = CorsiPhase.SHOWING,
    val userInput: List<Int> = emptyList(),
    val sequenceLength: Int = 2,
    val roundIndex: Int = 0,
    val lastWasCorrect: Boolean? = null
)

// 9 posiciones fijas de los bloques (similar al test de Corsi original)
val BLOCK_POSITIONS = listOf(
    BlockPosition(0.15f, 0.20f), BlockPosition(0.55f, 0.15f), BlockPosition(0.82f, 0.28f),
    BlockPosition(0.35f, 0.38f), BlockPosition(0.72f, 0.45f), BlockPosition(0.18f, 0.55f),
    BlockPosition(0.50f, 0.60f), BlockPosition(0.80f, 0.72f), BlockPosition(0.28f, 0.78f)
)

class CorsiEngine(override val config: CorsiConfig) :
    GameEngine<CorsiConfig, CorsiResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())
    private val _uiState = MutableStateFlow(CorsiUiState(sequenceLength = config.sequenceLength))
    val uiState: StateFlow<CorsiUiState> = _uiState.asStateFlow()
    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private var currentSeq    = listOf<Int>()
    private var userInput     = mutableListOf<Int>()
    private var roundIndex    = 0; private var correctRounds = 0; private var maxSpan = 0
    private var finalResult: CorsiResult? = null

    override fun start() { _state.value = GameState.Instructions }

    fun startCountdown() { scope.launch {
        for (i in 3 downTo 1) { _state.value = GameState.Countdown(i); delay(1000L) }
        nextRound()
    }}

    private suspend fun nextRound() {
        if (roundIndex >= config.totalRounds) { endGame(); return }
        currentSeq = List(config.sequenceLength) { Random.nextInt(9) }
        userInput  = mutableListOf()
        _uiState.value = _uiState.value.copy(phase = CorsiPhase.SHOWING, userInput = emptyList(), roundIndex = roundIndex)
        _state.value = GameState.Playing(roundIndex.toFloat() / config.totalRounds, null)

        for (blockIdx in currentSeq) {
            _uiState.value = _uiState.value.copy(highlightedBlock = blockIdx)
            delay(700L)
            _uiState.value = _uiState.value.copy(highlightedBlock = null)
            delay(300L)
        }
        metrics.recordStimulusShown()
        _uiState.value = _uiState.value.copy(phase = CorsiPhase.INPUT)
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_uiState.value.phase != CorsiPhase.INPUT) return InputFeedback.Ignored
        if (input !is UserInput.SelectOption) return InputFeedback.Ignored
        val blockIdx = input.optionIndex
        if (blockIdx !in 0..8) return InputFeedback.Ignored

        userInput.add(blockIdx)
        _uiState.value = _uiState.value.copy(userInput = userInput.toList())

        if (userInput.size == config.sequenceLength) {
            scope.launch { validateRound() }
        }
        return InputFeedback.Accepted
    }

    private suspend fun validateRound() {
        val isCorrect = userInput == currentSeq
        if (isCorrect) { correctRounds++; maxSpan = maxOf(maxSpan, config.sequenceLength) }
        metrics.recordUserResponse(isCorrect)
        _uiState.value = _uiState.value.copy(phase = CorsiPhase.FEEDBACK, lastWasCorrect = isCorrect)
        delay(1200L)
        roundIndex++
        nextRound()
    }

    private suspend fun endGame() {
        val snap = metrics.getMetrics()
        finalResult = CorsiResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel, maxCorrectSpan=maxSpan,
            correctRounds=correctRounds, totalRounds=config.totalRounds
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause()  {}; override fun resume() {}
    override fun forceEnd(): CorsiResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
