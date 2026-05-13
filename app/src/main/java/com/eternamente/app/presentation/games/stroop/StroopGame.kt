package com.eternamente.app.presentation.games.stroop

import androidx.compose.ui.graphics.Color
import com.eternamente.app.presentation.games.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

enum class InkColor(val label: String, val color: Color) {
    RED("Rojo", Color(0xFFE53935)), BLUE("Azul", Color(0xFF1E88E5)),
    GREEN("Verde", Color(0xFF43A047)), YELLOW("Amarillo", Color(0xFFFFB300))
}

data class StroopStimulus(val wordText: String, val wordColor: InkColor, val isCongruent: Boolean)

data class StroopConfig(
    override val gameId: String = GAME_ID, override val sessionId: String,
    override val userId: String, override val difficultyLevel: Int = 1,
    val totalStimuli: Int = 20, val stimulusDurationMs: Long = 3_000L
) : GameConfig {
    companion object {
        const val GAME_ID = "stroop"
        fun forDifficulty(level: Int, sessionId: String, userId: String) = StroopConfig(
            sessionId=sessionId, userId=userId, difficultyLevel=level,
            totalStimuli = 20, stimulusDurationMs = when(level){1->4000L;2->3000L;3->2500L;4->2000L;else->1500L}
        )
    }
}

data class StroopResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val correctCount: Int, val totalStimuli: Int,
    val congruentRT: Float, val incongruentRT: Float, val interferenceIndex: Float
) : GameResult

data class StroopUiState(
    val stimulus: StroopStimulus? = null, val stimulusIndex: Int = 0,
    val totalStimuli: Int = 20, val correctCount: Int = 0, val answered: Boolean = false
)

class StroopEngine(override val config: StroopConfig) :
    GameEngine<StroopConfig, StroopResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())
    private val _uiState = MutableStateFlow(StroopUiState(totalStimuli = config.totalStimuli))
    val uiState: StateFlow<StroopUiState> = _uiState.asStateFlow()
    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private val stimuli  = buildStimuli()
    private var idx      = 0; private var correctCount = 0; private var answered = false
    private val congruentRTs = mutableListOf<Float>(); private val incongruentRTs = mutableListOf<Float>()
    private var stimulusStartMs = 0L; private var finalResult: StroopResult? = null

    private fun buildStimuli(): List<StroopStimulus> = buildList {
        val congruentCount = (config.totalStimuli * 0.4).toInt()
        repeat(congruentCount) { val c = InkColor.entries.random(); add(StroopStimulus(c.label, c, true)) }
        repeat(config.totalStimuli - congruentCount) {
            val inkColor = InkColor.entries.random()
            val wordColor = InkColor.entries.filter { it != inkColor }.random()
            add(StroopStimulus(wordColor.label, inkColor, false))
        }
    }.shuffled()

    override fun start() { _state.value = GameState.Instructions }

    fun startCountdown() { scope.launch {
        for (i in 3 downTo 1) { _state.value = GameState.Countdown(i); delay(1000L) }
        runNextStimulus()
    }}

    private suspend fun runNextStimulus() {
        if (idx >= stimuli.size) { endGame(); return }
        val s = stimuli[idx]; answered = false
        _uiState.value = StroopUiState(stimulus = s, stimulusIndex = idx, totalStimuli = config.totalStimuli, correctCount = correctCount)
        _state.value = GameState.Playing(idx.toFloat() / stimuli.size, null)
        metrics.recordStimulusShown(); stimulusStartMs = System.currentTimeMillis()
        delay(config.stimulusDurationMs)
        if (!answered) { metrics.recordOmission() }
        idx++; runNextStimulus()
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing || answered) return InputFeedback.Ignored
        if (input !is UserInput.SelectOption) return InputFeedback.Ignored
        val selected = InkColor.entries.getOrNull(input.optionIndex) ?: return InputFeedback.Ignored
        val s = stimuli.getOrNull(idx) ?: return InputFeedback.Ignored
        answered = true
        val isCorrect = selected == s.wordColor
        val rt = (System.currentTimeMillis() - stimulusStartMs).toFloat()
        if (isCorrect) { correctCount++; if (s.isCongruent) congruentRTs.add(rt) else incongruentRTs.add(rt) }
        metrics.recordUserResponse(isCorrect)
        return if (isCorrect) InputFeedback.Correct else InputFeedback.Incorrect
    }

    private suspend fun endGame() {
        val snap = metrics.getMetrics()
        val cRT = if (congruentRTs.isNotEmpty()) congruentRTs.average().toFloat() else 0f
        val iRT = if (incongruentRTs.isNotEmpty()) incongruentRTs.average().toFloat() else 0f
        finalResult = StroopResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel, correctCount=correctCount,
            totalStimuli=config.totalStimuli, congruentRT=cRT, incongruentRT=iRT,
            interferenceIndex = if (cRT > 0) (iRT - cRT) / cRT else 0f
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause()  {}; override fun resume() {}
    override fun forceEnd(): StroopResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
