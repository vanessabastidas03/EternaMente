package com.eternamente.app.presentation.games.temporalorientation

import com.eternamente.app.presentation.games.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// ── Modelos ───────────────────────────────────────────────────────────────────

data class OrientationQuestion(
    val questionText: String,
    val correctAnswer: String,
    val options: List<String>   // 4 opciones, la correcta incluida
)

data class TemporalConfig(
    override val gameId: String = GAME_ID, override val sessionId: String,
    override val userId: String, override val difficultyLevel: Int = 1
) : GameConfig {
    companion object { const val GAME_ID = "temporal_orientation" }
}

data class TemporalResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val correctAnswers: Int, val totalQuestions: Int, val answers: List<Boolean>
) : GameResult

data class TemporalUiState(
    val questions: List<OrientationQuestion> = emptyList(),
    val currentIndex: Int                    = 0,
    val selectedAnswer: String?              = null,
    val isCorrect: Boolean?                  = null
)

// ── Generación de preguntas ────────────────────────────────────────────────────

fun buildOrientationQuestions(): List<OrientationQuestion> {
    val locale = Locale("es")
    val today  = LocalDate.now(ZoneId.systemDefault())
    val now    = LocalTime.now(ZoneId.systemDefault())

    val dayName  = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }
    val month    = today.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }
    val year     = today.year
    val dayNum   = today.dayOfMonth
    val season   = when (today.monthValue) { in 3..5 -> "Primavera"; in 6..8 -> "Verano"; in 9..11 -> "Otoño"; else -> "Invierno" }

    val daysOfWeek = listOf("Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo")
    val months     = listOf("Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")

    fun wrongDays(correct: String) = (daysOfWeek - correct).shuffled().take(3)
    fun wrongMonths(correct: String) = (months - correct).shuffled().take(3)
    fun nearNums(n: Int, range: Int, min: Int, max: Int) = (-range..range).filter { it != 0 }.map { (n+it).coerceIn(min, max) }.distinct().shuffled().take(3).map { "$it" }

    return listOf(
        OrientationQuestion("¿Qué día de la semana es hoy?", dayName, (wrongDays(dayName) + dayName).shuffled()),
        OrientationQuestion("¿Cuál es la fecha de hoy?", "$dayNum", (nearNums(dayNum, 3, 1, 31) + "$dayNum").shuffled()),
        OrientationQuestion("¿En qué mes estamos?", month, (wrongMonths(month) + month).shuffled()),
        OrientationQuestion("¿En qué año estamos?", "$year", ((listOf(year-2,year-1,year+1) + year).shuffled().map { "$it" })),
        OrientationQuestion("¿En qué estación del año estamos?", season, listOf("Primavera","Verano","Otoño","Invierno").shuffled())
    )
}

// ── Engine ────────────────────────────────────────────────────────────────────

class TemporalOrientationEngine(override val config: TemporalConfig) :
    GameEngine<TemporalConfig, TemporalResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())
    private val _uiState = MutableStateFlow(TemporalUiState())
    val uiState: StateFlow<TemporalUiState> = _uiState.asStateFlow()
    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private val questions  = buildOrientationQuestions()
    private var idx        = 0; private var correctCount = 0
    private val answers    = mutableListOf<Boolean>()
    private var finalResult: TemporalResult? = null

    override fun start() {
        _uiState.value = TemporalUiState(questions = questions, currentIndex = 0)
        _state.value = GameState.Instructions
    }

    fun begin() {
        _state.value = GameState.Playing(0f, null)
        metrics.recordStimulusShown()
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing) return InputFeedback.Ignored
        if (input !is UserInput.TapTarget) return InputFeedback.Ignored
        val selected = input.targetId
        val q        = questions.getOrNull(idx) ?: return InputFeedback.Ignored
        if (_uiState.value.selectedAnswer != null) return InputFeedback.Ignored

        val isCorrect = selected == q.correctAnswer
        if (isCorrect) correctCount++
        answers.add(isCorrect)
        metrics.recordUserResponse(isCorrect)
        _uiState.value = _uiState.value.copy(selectedAnswer = selected, isCorrect = isCorrect)

        scope.launch {
            delay(1000L)
            idx++
            if (idx >= questions.size) endGame()
            else {
                _uiState.value = _uiState.value.copy(currentIndex = idx, selectedAnswer = null, isCorrect = null)
                _state.value = GameState.Playing(idx.toFloat() / questions.size, null)
                metrics.recordStimulusShown()
            }
        }
        return if (isCorrect) InputFeedback.Correct else InputFeedback.Incorrect
    }

    private suspend fun endGame() {
        val snap = metrics.getMetrics()
        finalResult = TemporalResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel, correctAnswers=correctCount,
            totalQuestions=questions.size, answers=answers.toList()
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause()  {}; override fun resume() {}
    override fun forceEnd(): TemporalResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
