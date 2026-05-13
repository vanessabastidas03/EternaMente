package com.eternamente.app.presentation.games.mentalcalc

import com.eternamente.app.presentation.games.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

enum class ArithOp(val symbol: String) { ADD("+"), SUBTRACT("-"), MULTIPLY("×"), DIVIDE("÷") }

data class ArithProblem(val a: Int, val b: Int, val op: ArithOp, val answer: Int) {
    val text: String get() = "$a ${op.symbol} $b = ?"
}

data class MentalCalcConfig(
    override val gameId: String = GAME_ID, override val sessionId: String,
    override val userId: String, override val difficultyLevel: Int = 1,
    val totalProblems: Int = 10, val timeLimitMs: Long = 10_000L
) : GameConfig {
    companion object {
        const val GAME_ID = "mental_calc"
        fun forDifficulty(level: Int, sessionId: String, userId: String) = MentalCalcConfig(
            sessionId=sessionId, userId=userId, difficultyLevel=level,
            timeLimitMs = when(level){1->12_000L;2->10_000L;3->9_000L;4->8_000L;else->7_000L}
        )
    }
}

data class MentalCalcResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val correctCount: Int, val totalProblems: Int, val omissions: Int
) : GameResult

data class MentalCalcUiState(
    val problem: ArithProblem? = null,
    val currentInput: String   = "",
    val problemIndex: Int      = 0,
    val totalProblems: Int     = 10,
    val timeRemainingMs: Long  = 10_000L,
    val lastWasCorrect: Boolean? = null
)

fun generateProblem(level: Int): ArithProblem {
    return when (level) {
        1    -> { val a = Random.nextInt(1, 11); val b = Random.nextInt(1, 11); ArithProblem(a, b, ArithOp.ADD, a+b) }
        2    -> { val a = Random.nextInt(10, 51); val b = Random.nextInt(1, 21); if (Random.nextBoolean()) ArithProblem(a,b,ArithOp.ADD,a+b) else ArithProblem(a,b,ArithOp.SUBTRACT,a-b) }
        3    -> { val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10); ArithProblem(a, b, ArithOp.MULTIPLY, a*b) }
        4    -> { val b = Random.nextInt(2, 9); val ans = Random.nextInt(2, 9); val a = b*ans; if (Random.nextBoolean()) ArithProblem(a,b,ArithOp.DIVIDE,ans) else { val c = Random.nextInt(2,10); val d = Random.nextInt(2,10); ArithProblem(c,d,ArithOp.MULTIPLY,c*d) } }
        else -> { val ops = listOf(ArithOp.ADD,ArithOp.SUBTRACT,ArithOp.MULTIPLY); val op = ops.random(); val a = Random.nextInt(5,20); val b = Random.nextInt(2,15); when(op){ArithOp.ADD->ArithProblem(a,b,op,a+b);ArithOp.SUBTRACT->ArithProblem(a+b,b,op,a);else->ArithProblem(a,b,op,a*b)} }
    }
}

class MentalCalcEngine(override val config: MentalCalcConfig) :
    GameEngine<MentalCalcConfig, MentalCalcResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())
    private val _uiState = MutableStateFlow(MentalCalcUiState(totalProblems = config.totalProblems))
    val uiState: StateFlow<MentalCalcUiState> = _uiState.asStateFlow()
    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private val problems = List(config.totalProblems) { generateProblem(config.difficultyLevel) }
    private var idx = 0; private var correctCount = 0; private var omissions = 0
    private var answered = false; private var timerJob: Job? = null
    private var finalResult: MentalCalcResult? = null

    override fun start() { _state.value = GameState.Instructions }

    fun begin() { scope.launch { for (i in 3 downTo 1) { _state.value = GameState.Countdown(i); delay(1000L) }; showProblem() } }

    private suspend fun showProblem() {
        if (idx >= problems.size) { endGame(); return }
        answered = false
        val p = problems[idx]
        _uiState.value = MentalCalcUiState(problem = p, problemIndex = idx, totalProblems = config.totalProblems, timeRemainingMs = config.timeLimitMs)
        _state.value = GameState.Playing(idx.toFloat() / problems.size, null)
        metrics.recordStimulusShown()
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            var remaining = config.timeLimitMs
            while (remaining > 0 && !answered) {
                delay(100L); remaining -= 100L
                _uiState.value = _uiState.value.copy(timeRemainingMs = remaining)
            }
            if (!answered) { omissions++; metrics.recordOmission(); _uiState.value = _uiState.value.copy(lastWasCorrect = false); delay(600L); idx++; showProblem() }
        }
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing || answered) return InputFeedback.Ignored
        when (input) {
            is UserInput.SelectOption -> {
                val digit = input.optionIndex
                if (digit == -1) { // borrar
                    val s = _uiState.value.currentInput
                    _uiState.value = _uiState.value.copy(currentInput = if (s.isNotEmpty()) s.dropLast(1) else s)
                } else if (digit == -2) { // confirmar
                    submitAnswer()
                } else if (_uiState.value.currentInput.length < 5) {
                    _uiState.value = _uiState.value.copy(currentInput = _uiState.value.currentInput + digit.toString())
                }
            }
            else -> return InputFeedback.Ignored
        }
        return InputFeedback.Accepted
    }

    private fun submitAnswer() {
        answered = true; timerJob?.cancel()
        val input   = _uiState.value.currentInput.toIntOrNull()
        val correct = problems[idx].answer
        val isOK    = input == correct
        if (isOK) correctCount++
        metrics.recordUserResponse(isOK)
        _uiState.value = _uiState.value.copy(lastWasCorrect = isOK)
        scope.launch { delay(600L); idx++; showProblem() }
    }

    private suspend fun endGame() {
        val snap = metrics.getMetrics()
        finalResult = MentalCalcResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel,
            correctCount=correctCount, totalProblems=config.totalProblems, omissions=omissions
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause() {}; override fun resume() {}
    override fun forceEnd(): MentalCalcResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
