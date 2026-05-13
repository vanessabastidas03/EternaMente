package com.eternamente.app.presentation.games.digitspan

import com.eternamente.app.presentation.games.engine.GameEngine
import com.eternamente.app.presentation.games.engine.GameState
import com.eternamente.app.presentation.games.engine.InputFeedback
import com.eternamente.app.presentation.games.engine.MetricsCollector
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
import kotlin.random.Random

/**
 * Motor del Digit Span (Secuencia de Números).
 *
 * Flujo por ronda:
 * 1. Generar secuencia aleatoria de [DigitSpanConfig.sequenceLength] dígitos.
 * 2. Mostrar cada dígito [DIGIT_SHOW_MS] ms con [DIGIT_BLANK_MS] ms de intervalo.
 * 3. Cambiar a fase INPUT — el usuario teclea los dígitos con el numpad.
 * 4. Cuando se ingresan N dígitos, validar contra la secuencia
 *    (forward o backward según [DigitSpanConfig.isBackward]).
 * 5. Mostrar feedback [FEEDBACK_MS] ms y pasar a la siguiente ronda.
 * 6. Tras [TOTAL_ROUNDS] rondas, emitir [GameState.Completed].
 */
class DigitSpanEngine(
    override val config: DigitSpanConfig
) : GameEngine<DigitSpanConfig, DigitSpanResult> {

    private val _state    = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()

    override val metrics  = MetricsCollector(SystemTimeProvider())

    private val _uiState  = MutableStateFlow(DigitSpanUiState(sequenceLength = config.sequenceLength, isBackward = config.isBackward))
    val uiState: StateFlow<DigitSpanUiState> = _uiState.asStateFlow()

    private val scope     = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private var currentSequence  = listOf<Int>()
    private var currentInput     = mutableListOf<Int>()
    private var positionErrors   = mutableListOf<Int>()
    private var maxCorrectSpan   = 0
    private var correctRounds    = 0
    private var totalRounds      = 0
    private var roundIndex       = 0
    private var finalResult: DigitSpanResult? = null

    override fun start() { _state.value = GameState.Instructions }

    fun startCountdown() {
        scope.launch {
            for (i in 3 downTo 1) { _state.value = GameState.Countdown(i); delay(1000L) }
            nextRound()
        }
    }

    private suspend fun nextRound() {
        if (roundIndex >= TOTAL_ROUNDS) { endGame(); return }

        currentSequence = generateSequence()
        currentInput    = mutableListOf()
        totalRounds++
        _uiState.value  = DigitSpanUiState(
            phase          = SpanPhase.SHOWING,
            sequenceLength = config.sequenceLength,
            isBackward     = config.isBackward,
            roundIndex     = roundIndex
        )
        _state.value = GameState.Playing(progress = roundIndex.toFloat() / TOTAL_ROUNDS, timeLeft = null)

        // Mostrar cada dígito
        for (digit in currentSequence) {
            _uiState.value = _uiState.value.copy(displayedDigit = digit)
            delay(DIGIT_SHOW_MS)
            _uiState.value = _uiState.value.copy(displayedDigit = null)
            delay(DIGIT_BLANK_MS)
        }

        // Cambiar a fase de input
        metrics.recordStimulusShown()
        _uiState.value = _uiState.value.copy(phase = SpanPhase.INPUT)
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_uiState.value.phase != SpanPhase.INPUT)       return InputFeedback.Ignored
        if (input !is UserInput.SelectOption)              return InputFeedback.Ignored
        val digit = input.optionIndex
        if (digit !in 0..9)                                return InputFeedback.Ignored

        currentInput.add(digit)
        _uiState.value = _uiState.value.copy(userInput = currentInput.toList())

        return if (currentInput.size == config.sequenceLength) {
            scope.launch { validateAndContinue() }
            InputFeedback.Accepted
        } else {
            InputFeedback.Accepted
        }
    }

    private suspend fun validateAndContinue() {
        metrics.recordUserResponse(isCorrect = isCurrentCorrect())

        val target  = if (config.isBackward) currentSequence.reversed() else currentSequence
        val errors  = currentInput.indices.filter { i -> i < target.size && currentInput[i] != target[i] }
        positionErrors.addAll(errors)

        val isCorrect = currentInput == target
        if (isCorrect) { correctRounds++; maxCorrectSpan = maxOf(maxCorrectSpan, config.sequenceLength) }

        // Feedback breve
        _uiState.value = _uiState.value.copy(phase = SpanPhase.FEEDBACK, lastWasCorrect = isCorrect)
        delay(FEEDBACK_MS)

        roundIndex++
        nextRound()
    }

    private fun isCurrentCorrect(): Boolean {
        val target = if (config.isBackward) currentSequence.reversed() else currentSequence
        return currentInput == target
    }

    private fun generateSequence(): List<Int> =
        List(config.sequenceLength) { Random.nextInt(1, 10) }  // 1-9 para evitar 0 al inicio

    override fun pause()  {}  // Digit Span no admite pausa durante showing
    override fun resume() {}

    private suspend fun endGame() {
        val snapshot = metrics.getMetrics()
        finalResult  = DigitSpanResult(
            gameId          = config.gameId,
            sessionId       = config.sessionId,
            metrics         = snapshot,
            difficultyReached = config.difficultyLevel,
            sequenceLength  = config.sequenceLength,
            maxCorrectSpan  = maxCorrectSpan,
            positionErrors  = positionErrors.toList(),
            isBackward      = config.isBackward,
            totalRounds     = totalRounds,
            correctRounds   = correctRounds
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun forceEnd(): DigitSpanResult = finalResult ?: runBlocking {
        endGame(); finalResult!!
    }

    companion object {
        const val DIGIT_SHOW_MS = 1_000L
        const val DIGIT_BLANK_MS = 300L
        const val FEEDBACK_MS   = 1_200L
        const val TOTAL_ROUNDS  = 5
    }
}
