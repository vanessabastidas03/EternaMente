package com.eternamente.app.presentation.games.readingcomprehension

import com.eternamente.app.presentation.games.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

data class ReadingPassage(
    val title: String,
    val text: String,
    val questions: List<CompQuestion>
)

data class CompQuestion(val question: String, val options: List<String>, val correctIndex: Int)

val READING_PASSAGES = listOf(
    ReadingPassage("El Mercado del Barrio", """
Doña Carmen va al mercado cada martes. Primero pasa por el puesto de frutas, donde compra naranjas y plátanos.
Luego visita a don Pedro, el pescadero, que siempre le guarda el mejor salmón.
Finalmente compra pan en la panadería de la esquina. A Carmen le gusta llegar temprano para encontrar los mejores productos frescos y charlar con los vecinos.
    """.trimIndent(), listOf(
        CompQuestion("¿Qué día va Carmen al mercado?", listOf("Lunes","Martes","Miércoles","Sábado"), 1),
        CompQuestion("¿Qué le guarda don Pedro a Carmen?", listOf("Merluza","Atún","Salmón","Bacalao"), 2),
        CompQuestion("¿Por qué le gusta llegar temprano?", listOf("Para evitar el calor","Para encontrar productos frescos","Porque cierra pronto","Para hacer ejercicio"), 1)
    )),
    ReadingPassage("Una Tarde en el Parque", """
El parque del barrio es el lugar favorito de Arturo. Cada tarde, después de comer, sale a caminar por sus senderos.
Le gusta observar los patos del estanque y alimentar a las palomas con migas de pan.
Cuando hace buen tiempo, se sienta en su banco favorito bajo el pino grande y lee el periódico.
A veces se encuentra con su amigo Luis y juegan una partida de ajedrez.
    """.trimIndent(), listOf(
        CompQuestion("¿Cuándo va Arturo al parque?", listOf("Por la mañana","Después de comer","Al anochecer","Los fines de semana"), 1),
        CompQuestion("¿Con qué alimenta a las palomas?", listOf("Semillas","Comida para pájaros","Migas de pan","Maíz"), 2),
        CompQuestion("¿Qué hace cuando se encuentra con Luis?", listOf("Dan un paseo","Juegan ajedrez","Toman café","Leen juntos"), 1)
    )),
    ReadingPassage("La Receta de la Abuela", """
La tarta de manzana de la abuela Rosa es famosa en toda la familia. Su secreto está en usar manzanas de la huerta,
que son más dulces que las del supermercado. Primero pela y corta las manzanas en láminas finas.
Luego las mezcla con azúcar, canela y un poco de limón. La masa la prepara con harina, mantequilla y huevos.
Cuando sale del horno, el olor llena toda la casa.
    """.trimIndent(), listOf(
        CompQuestion("¿Cómo se llama la abuela de la historia?", listOf("Carmen","María","Rosa","Ana"), 2),
        CompQuestion("¿Por qué son mejores las manzanas de la huerta?", listOf("Son más grandes","Son más dulces","Son más baratas","Son más frescas"), 1),
        CompQuestion("¿Qué ingredientes lleva la mezcla de manzanas?", listOf("Azúcar, canela y limón","Azúcar, vainilla y naranja","Miel, canela y limón","Azúcar y mantequilla"), 0)
    ))
)

data class ReadingConfig(
    override val gameId: String = GAME_ID, override val sessionId: String,
    override val userId: String, override val difficultyLevel: Int = 1,
    val hideTextForQuestions: Boolean = false
) : GameConfig {
    companion object {
        const val GAME_ID = "reading_comprehension"
        fun forDifficulty(level: Int, sessionId: String, userId: String) = ReadingConfig(
            sessionId=sessionId, userId=userId, difficultyLevel=level,
            hideTextForQuestions = level >= 3
        )
    }
}

data class ReadingResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val correctAnswers: Int, val totalQuestions: Int, val readingTimeMs: Long
) : GameResult

enum class ReadingPhase { READING, QUESTIONS }

data class ReadingUiState(
    val passage: ReadingPassage? = null,
    val phase: ReadingPhase      = ReadingPhase.READING,
    val currentQuestion: Int     = 0,
    val selectedAnswer: Int?     = null,
    val isCorrect: Boolean?      = null,
    val showText: Boolean        = true
)

class ReadingCompEngine(override val config: ReadingConfig) :
    GameEngine<ReadingConfig, ReadingResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())
    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()
    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private val passage       = READING_PASSAGES.random()
    private var questionIdx   = 0; private var correctCount = 0
    private var readingStartMs = 0L; private var readingTimeMs = 0L
    private var answered      = false; private var finalResult: ReadingResult? = null

    override fun start() { _state.value = GameState.Instructions }

    fun beginReading() {
        readingStartMs = SystemTimeProvider().uptimeMillis()
        _uiState.value = ReadingUiState(passage = passage, phase = ReadingPhase.READING, showText = true)
        _state.value   = GameState.Playing(0f, null)
        metrics.recordStimulusShown()
    }

    fun proceedToQuestions() {
        readingTimeMs  = SystemTimeProvider().uptimeMillis() - readingStartMs
        _uiState.value = _uiState.value.copy(phase = ReadingPhase.QUESTIONS, showText = !config.hideTextForQuestions)
        metrics.recordStimulusShown()
        _state.value   = GameState.Playing(0f, null)
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing || answered) return InputFeedback.Ignored
        if (input !is UserInput.SelectOption || _uiState.value.phase != ReadingPhase.QUESTIONS) return InputFeedback.Ignored
        val optIdx    = input.optionIndex
        val q         = passage.questions.getOrNull(questionIdx) ?: return InputFeedback.Ignored
        answered      = true
        val isCorrect = optIdx == q.correctIndex
        if (isCorrect) correctCount++
        metrics.recordUserResponse(isCorrect)
        _uiState.value = _uiState.value.copy(selectedAnswer = optIdx, isCorrect = isCorrect)

        scope.launch {
            delay(1000L)
            questionIdx++
            if (questionIdx >= passage.questions.size) endGame()
            else {
                answered = false
                _uiState.value = _uiState.value.copy(currentQuestion = questionIdx, selectedAnswer = null, isCorrect = null)
                _state.value   = GameState.Playing(questionIdx.toFloat() / passage.questions.size, null)
                metrics.recordStimulusShown()
            }
        }
        return if (isCorrect) InputFeedback.Correct else InputFeedback.Incorrect
    }

    private suspend fun endGame() {
        val snap = metrics.getMetrics()
        finalResult = ReadingResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel,
            correctAnswers=correctCount, totalQuestions=passage.questions.size,
            readingTimeMs=readingTimeMs
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause() {}; override fun resume() {}
    override fun forceEnd(): ReadingResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
