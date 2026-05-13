package com.eternamente.app.presentation.games.facename

import com.eternamente.app.presentation.games.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

// ── Base de datos de pares ────────────────────────────────────────────────────

data class AvatarPerson(val emoji: String, val name: String, val hint: String)

val AVATAR_DATABASE = listOf(
    AvatarPerson("👩‍🦰","Ana García","Mujer, pelo rojizo"),
    AvatarPerson("👨‍🦳","Carlos López","Hombre, pelo canoso"),
    AvatarPerson("👴","Roberto Martínez","Hombre mayor"),
    AvatarPerson("👵","María Rodríguez","Mujer mayor"),
    AvatarPerson("🧑‍🦱","Diego Hernández","Joven, pelo rizado"),
    AvatarPerson("👩‍🦱","Lucía Fernández","Mujer, pelo rizado"),
    AvatarPerson("👨‍🦲","Pedro Sánchez","Hombre calvo"),
    AvatarPerson("👩‍🦳","Carmen Torres","Mujer, pelo blanco"),
    AvatarPerson("🧓","Jorge Ruiz","Hombre maduro"),
    AvatarPerson("👩","Elena Morales","Mujer joven")
)

// ── Modelos ───────────────────────────────────────────────────────────────────

data class FaceNameConfig(
    override val gameId: String = GAME_ID, override val sessionId: String,
    override val userId: String, override val difficultyLevel: Int = 1,
    val pairsToStudy: Int = 5, val studyDurationMs: Long = 30_000L
) : GameConfig {
    companion object {
        const val GAME_ID = "face_name"
        fun forDifficulty(level: Int, sessionId: String, userId: String) = FaceNameConfig(
            sessionId=sessionId, userId=userId, difficultyLevel=level,
            pairsToStudy = if (level <= 2) 4 else if (level <= 4) 5 else 6,
            studyDurationMs = when(level){1->40_000L;2->30_000L;3->25_000L;4->20_000L;else->15_000L}
        )
    }
}

data class FaceNameResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val correctRecalls: Int, val totalPairs: Int, val confusions: Int
) : GameResult

enum class FaceNamePhase { STUDY, TEST }

data class FaceNameUiState(
    val phase: FaceNamePhase          = FaceNamePhase.STUDY,
    val studyPairs: List<AvatarPerson> = emptyList(),
    val studyTimeLeftMs: Long          = 30_000L,
    val currentTestIndex: Int          = 0,
    val currentFace: AvatarPerson?     = null,
    val testOptions: List<String>      = emptyList(),
    val selectedAnswer: String?        = null,
    val isCorrect: Boolean?            = null
)

// ── Engine ────────────────────────────────────────────────────────────────────

class FaceNameEngine(override val config: FaceNameConfig) :
    GameEngine<FaceNameConfig, FaceNameResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())
    private val _uiState = MutableStateFlow(FaceNameUiState())
    val uiState: StateFlow<FaceNameUiState> = _uiState.asStateFlow()
    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private val studyPairs   = AVATAR_DATABASE.shuffled().take(config.pairsToStudy)
    private var testIdx      = 0; private var correctCount = 0; private var confusions = 0
    private var answered     = false; private var finalResult: FaceNameResult? = null

    override fun start() { _state.value = GameState.Instructions }

    fun begin() {
        scope.launch {
            // Fase de estudio
            _uiState.value = FaceNameUiState(phase = FaceNamePhase.STUDY, studyPairs = studyPairs, studyTimeLeftMs = config.studyDurationMs)
            _state.value   = GameState.Playing(0f, (config.studyDurationMs / 1000).toInt())
            metrics.recordStimulusShown()

            var remaining = config.studyDurationMs
            while (remaining > 0) {
                delay(500L); remaining -= 500L
                _uiState.value = _uiState.value.copy(studyTimeLeftMs = remaining)
            }
            // Fase de prueba
            startTest()
        }
    }

    private suspend fun startTest() {
        testIdx = 0; showNextTest()
    }

    private suspend fun showNextTest() {
        if (testIdx >= studyPairs.size) { endGame(); return }
        answered = false
        val face    = studyPairs[testIdx]
        val wrongs  = (AVATAR_DATABASE - studyPairs.toSet() + studyPairs - face).map { it.name }.shuffled().take(3)
        val options = (wrongs + face.name).shuffled()
        _uiState.value = FaceNameUiState(
            phase = FaceNamePhase.TEST, studyPairs = studyPairs,
            currentTestIndex = testIdx, currentFace = face, testOptions = options
        )
        _state.value = GameState.Playing(testIdx.toFloat() / studyPairs.size, null)
        metrics.recordStimulusShown()
    }

    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing) return InputFeedback.Ignored
        if (_uiState.value.phase != FaceNamePhase.TEST || answered) return InputFeedback.Ignored
        if (input !is UserInput.TapTarget) return InputFeedback.Ignored
        val selected = input.targetId
        val face     = _uiState.value.currentFace ?: return InputFeedback.Ignored

        answered = true
        val isCorrect = selected == face.name
        if (isCorrect) correctCount++ else confusions++
        metrics.recordUserResponse(isCorrect)
        _uiState.value = _uiState.value.copy(selectedAnswer = selected, isCorrect = isCorrect)

        scope.launch {
            delay(1000L); testIdx++; showNextTest()
        }
        return if (isCorrect) InputFeedback.Correct else InputFeedback.Incorrect
    }

    private suspend fun endGame() {
        val snap = metrics.getMetrics()
        finalResult = FaceNameResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel,
            correctRecalls=correctCount, totalPairs=studyPairs.size, confusions=confusions
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause() {}; override fun resume() {}
    override fun forceEnd(): FaceNameResult = finalResult ?: runBlocking { endGame(); finalResult!! }
}
