package com.eternamente.app.presentation.games.namingimage

import com.eternamente.app.presentation.games.engine.GameEngine
import com.eternamente.app.presentation.games.engine.GameState
import com.eternamente.app.presentation.games.engine.InputFeedback
import com.eternamente.app.presentation.games.engine.MetricsCollector
import com.eternamente.app.presentation.games.engine.SystemTimeProvider
import com.eternamente.app.presentation.games.engine.UserInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class PresentedItem(
    val item: ImageItem,
    val options: List<String>,    // 4 opciones mezcladas
    val correctIndex: Int         // índice de la correcta en options
)

class NamingImageEngine(override val config: NamingImageConfig) :
    GameEngine<NamingImageConfig, NamingImageResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())

    private val _uiState = MutableStateFlow(NamingImageUiState(totalImages = config.imagesPerSession))
    val uiState: StateFlow<NamingImageUiState> = _uiState.asStateFlow()

    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private var timerJob: Job? = null

    private val presentedItems: List<PresentedItem> = buildItems()
    private var currentIndex   = 0
    private var correctCount   = 0
    private var semanticErrors = 0
    private var unrelatedErrors = 0
    private var omissions      = 0
    private var answered       = false
    private var finalResult: NamingImageResult? = null

    private fun buildItems(): List<PresentedItem> {
        val selected = IMAGE_DATABASE.shuffled().take(config.imagesPerSession)
        return selected.map { item ->
            val distractors = (item.semanticDistractors + item.unrelatedDistractor).take(3)
            val all = (listOf(item.correctName) + distractors).shuffled()
            PresentedItem(item, all, all.indexOf(item.correctName))
        }
    }

    override fun start() { _state.value = GameState.Instructions }

    fun startCountdown() {
        scope.launch {
            for (i in 3 downTo 1) { _state.value = GameState.Countdown(i); delay(1000L) }
            showNextImage()
        }
    }

    private suspend fun showNextImage() {
        if (currentIndex >= presentedItems.size) { endGame(); return }
        val pi = presentedItems[currentIndex]
        answered = false
        _uiState.value = NamingImageUiState(
            emoji = pi.item.emoji, options = pi.options, correctName = pi.item.correctName,
            imageIndex = currentIndex, totalImages = config.imagesPerSession,
            timeRemainingMs = config.timeLimitPerImageMs
        )
        _state.value = GameState.Playing(currentIndex.toFloat() / config.imagesPerSession, null)
        metrics.recordStimulusShown()

        // Timer por imagen
        timerJob?.cancel()
        timerJob = scope.launch {
            var remaining = config.timeLimitPerImageMs
            while (remaining > 0 && !answered) {
                delay(100L); remaining -= 100L
                _uiState.value = _uiState.value.copy(timeRemainingMs = remaining)
            }
            if (!answered) { // tiempo agotado
                omissions++
                metrics.recordOmission()
                delay(500L)
                currentIndex++
                showNextImage()
            }
        }
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (answered) return InputFeedback.Ignored
        if (input !is UserInput.SelectOption) return InputFeedback.Ignored
        val optionIdx = input.optionIndex
        if (_state.value !is GameState.Playing) return InputFeedback.Ignored

        answered = true
        timerJob?.cancel()
        val pi = presentedItems.getOrNull(currentIndex) ?: return InputFeedback.Ignored
        val selected = pi.options.getOrNull(optionIdx) ?: return InputFeedback.Ignored
        val isCorrect = optionIdx == pi.correctIndex

        metrics.recordUserResponse(isCorrect = isCorrect)
        if (isCorrect) correctCount++
        else {
            if (selected in pi.item.semanticDistractors) semanticErrors++ else unrelatedErrors++
        }
        _uiState.value = _uiState.value.copy(selectedIndex = optionIdx, isCorrect = isCorrect)

        scope.launch {
            delay(FEEDBACK_MS)
            currentIndex++
            showNextImage()
        }
        return if (isCorrect) InputFeedback.Correct else InputFeedback.Incorrect
    }

    private suspend fun endGame() {
        val snap = metrics.getMetrics()
        finalResult = NamingImageResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel,
            correctAnswers=correctCount, totalImages=config.imagesPerSession,
            semanticErrors=semanticErrors, unrelatedErrors=unrelatedErrors, omissions=omissions
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause()  {}
    override fun resume() {}
    override fun forceEnd(): NamingImageResult = finalResult ?: runBlocking { endGame(); finalResult!! }

    companion object { const val FEEDBACK_MS = 800L }
}
