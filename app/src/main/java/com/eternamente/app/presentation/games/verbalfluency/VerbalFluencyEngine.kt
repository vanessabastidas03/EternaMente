package com.eternamente.app.presentation.games.verbalfluency

import com.eternamente.app.presentation.games.engine.GameEngine
import com.eternamente.app.presentation.games.engine.GameState
import com.eternamente.app.presentation.games.engine.GameTimer
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

class VerbalFluencyEngine(override val config: VerbalFluencyConfig) :
    GameEngine<VerbalFluencyConfig, VerbalFluencyResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())

    private val _uiState = MutableStateFlow(VerbalFluencyUiState(category = config.category, timeLeft = config.timeLimitSeconds))
    val uiState: StateFlow<VerbalFluencyUiState> = _uiState.asStateFlow()

    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private var timeLeft = config.timeLimitSeconds

    private val validWords   = mutableListOf<String>()
    private var repetitions  = 0
    private var intrusions   = 0
    private val seenWords    = mutableSetOf<String>()
    private var finalResult: VerbalFluencyResult? = null

    private val timer = GameTimer(
        durationSeconds = config.timeLimitSeconds,
        timeProvider    = SystemTimeProvider(),
        scope           = scope,
        onTick          = { left ->
            timeLeft = left
            _uiState.value = _uiState.value.copy(timeLeft = left)
            _state.value = GameState.Playing(1f - left.toFloat() / config.timeLimitSeconds, left)
        },
        onComplete = { scope.launch { endGame() } }
    )

    override fun start() { _state.value = GameState.Instructions }

    fun startCountdown() {
        scope.launch {
            for (i in 3 downTo 1) { _state.value = GameState.Countdown(i); delay(1000L) }
            _state.value = GameState.Playing(0f, config.timeLimitSeconds)
            metrics.recordStimulusShown()
            timer.start()
        }
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing) return InputFeedback.Ignored
        // TextInput llega con la palabra a validar
        val word = when (input) {
            is UserInput.TapTarget -> input.targetId.lowercase().trim()
            else -> return InputFeedback.Ignored
        }
        if (word.isBlank()) return InputFeedback.Ignored

        val normalized = word.lowercase().trim()
        val isValid    = normalized in config.category.words
        val isDuplicate = normalized in seenWords

        return when {
            isDuplicate -> { repetitions++; InputFeedback.Incorrect }
            isValid     -> {
                seenWords.add(normalized)
                validWords.add(normalized)
                metrics.recordUserResponse(isCorrect = true)
                _uiState.value = _uiState.value.copy(enteredWords = validWords.toList(), lastWordValid = true)
                InputFeedback.Correct
            }
            else -> {
                intrusions++
                metrics.recordUserResponse(isCorrect = false)
                _uiState.value = _uiState.value.copy(lastWordValid = false)
                InputFeedback.Incorrect
            }
        }
    }

    private suspend fun endGame() {
        val snap = metrics.getMetrics()
        val wpm  = validWords.size.toFloat() / (config.timeLimitSeconds / 60f)
        finalResult = VerbalFluencyResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel, category=config.category,
            validWords=validWords.toList(), repetitions=repetitions, intrusions=intrusions,
            wordsPerMinute=wpm
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause()  { timer.pause();  _state.value = GameState.Paused }
    override fun resume() { timer.resume(); _state.value = GameState.Playing(1f - timeLeft.toFloat() / config.timeLimitSeconds, timeLeft) }
    override fun forceEnd(): VerbalFluencyResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
