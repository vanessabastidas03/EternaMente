package com.eternamente.app.presentation.games.flashcolor

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
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class FlashColorEngine(override val config: FlashColorConfig) :
    GameEngine<FlashColorConfig, FlashColorResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())

    private val _uiState = MutableStateFlow(FlashColorUiState(targetColor = config.targetColor, totalStimuli = config.totalStimuli))
    val uiState: StateFlow<FlashColorUiState> = _uiState.asStateFlow()

    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    // Generar estímulos: 30% targets, 70% distractores
    private val stimuli: List<StimulusColor> = buildList {
        val targetCount = (config.totalStimuli * 0.30).toInt()
        repeat(targetCount)                    { add(config.targetColor) }
        repeat(config.totalStimuli - targetCount) {
            add(StimulusColor.entries.filter { it != config.targetColor }.random())
        }
    }.shuffled()

    private var hits = 0; private var misses = 0
    private var falseAlarms = 0; private var correctRejections = 0
    private var respondedThisStimulus = false
    private var currentStimulusIndex = -1
    private var finalResult: FlashColorResult? = null

    override fun start() { _state.value = GameState.Instructions }

    fun startCountdown() {
        scope.launch {
            for (i in 3 downTo 1) { _state.value = GameState.Countdown(i); delay(1000L) }
            runAllStimuli()
        }
    }

    private suspend fun runAllStimuli() {
        for ((idx, color) in stimuli.withIndex()) {
            currentStimulusIndex = idx
            respondedThisStimulus = false

            _uiState.value = _uiState.value.copy(currentColor = color, stimulusIndex = idx, isShowingStimulus = true)
            _state.value   = GameState.Playing(progress = idx.toFloat() / stimuli.size, timeLeft = null)

            if (color == config.targetColor) metrics.recordStimulusShown()

            delay(config.stimulusDurationMs)

            // Si era target y no respondió → miss
            if (color == config.targetColor && !respondedThisStimulus) {
                misses++
                metrics.recordOmission()
            }
            // Si no era target y no respondió → correct rejection
            if (color != config.targetColor && !respondedThisStimulus) correctRejections++

            _uiState.value = _uiState.value.copy(currentColor = null, isShowingStimulus = false)
            delay(config.isiDurationMs)

            _uiState.value = _uiState.value.copy(hits = hits, misses = misses, falseAlarms = falseAlarms)
        }
        endGame()
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (input !is UserInput.Tap) return InputFeedback.Ignored
        if (!_uiState.value.isShowingStimulus) return InputFeedback.Ignored
        if (respondedThisStimulus) return InputFeedback.Ignored

        respondedThisStimulus = true
        val isTarget = stimuli.getOrNull(currentStimulusIndex) == config.targetColor

        return if (isTarget) {
            hits++
            metrics.recordUserResponse(isCorrect = true)
            InputFeedback.Correct
        } else {
            falseAlarms++
            metrics.recordUserResponse(isCorrect = false)
            InputFeedback.Incorrect
        }
    }

    private suspend fun endGame() {
        val snapshot  = metrics.getMetrics()
        val total     = stimuli.size
        val targetN   = stimuli.count { it == config.targetColor }
        val distN     = total - targetN
        val hitRate   = if (targetN > 0) hits.toFloat() / targetN else 0f
        val faRate    = if (distN  > 0) falseAlarms.toFloat() / distN else 0f
        val dPrime    = computeDPrime(hitRate, faRate)

        finalResult = FlashColorResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snapshot,
            difficultyReached=config.difficultyLevel, targetColor=config.targetColor,
            hits=hits, misses=misses, falseAlarms=falseAlarms, correctRejections=correctRejections,
            dPrime=dPrime, hitRate=hitRate, faRate=faRate
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    /** d-prime via inverse normal CDF (Abramowitz & Stegun approximation). */
    internal fun computeDPrime(hitRate: Float, faRate: Float): Float {
        val h = hitRate.coerceIn(0.01f, 0.99f).toDouble()
        val f = faRate.coerceIn(0.01f, 0.99f).toDouble()
        return (erfInv(2 * h - 1) - erfInv(2 * f - 1)).toFloat() * sqrt(2f)
    }

    private fun erfInv(x: Double): Double {
        val a   = 0.147
        val ln1 = ln(1.0 - x * x)
        val c   = 2.0 / (Math.PI * a) + ln1 / 2.0
        return (if (x >= 0) 1.0 else -1.0) * sqrt(sqrt(c * c - ln1 / a) - c)
    }

    override fun pause()  {}
    override fun resume() {}
    override fun forceEnd(): FlashColorResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
