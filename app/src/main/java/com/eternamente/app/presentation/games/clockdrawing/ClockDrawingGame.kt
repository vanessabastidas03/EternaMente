package com.eternamente.app.presentation.games.clockdrawing

import androidx.compose.ui.geometry.Offset
import com.eternamente.app.presentation.games.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlin.math.*

// ── Modelos ───────────────────────────────────────────────────────────────────

data class ClockTime(val hour: Int, val minute: Int) {
    /** Ángulo de la manecilla de horas desde las 12 en sentido horario (0–360°). */
    val expectedHourAngle: Float get() = (hour % 12 * 30f) + (minute * 0.5f)
    /** Ángulo de la manecilla de minutos (0–360°). */
    val expectedMinuteAngle: Float get() = minute * 6f
    val label: String get() = "${hour}:${minute.toString().padStart(2, '0')}"
}

val CLOCK_TARGETS = listOf(
    ClockTime(10, 10), ClockTime(3, 0),  ClockTime(6, 30),
    ClockTime(9, 15),  ClockTime(12, 0), ClockTime(7, 45),
    ClockTime(2, 30),  ClockTime(11, 5), ClockTime(4, 20), ClockTime(8, 40)
)

enum class ClockPhase { DRAW_HOUR, DRAW_MINUTE, DONE }

data class ClockDrawingConfig(
    override val gameId: String = GAME_ID,
    override val sessionId: String, override val userId: String,
    override val difficultyLevel: Int = 1,
    val angleTolerance: Float = 15f   // ±15 grados
) : GameConfig {
    companion object {
        const val GAME_ID = "clock_drawing"
        fun forDifficulty(level: Int, sessionId: String, userId: String) =
            ClockDrawingConfig(sessionId=sessionId, userId=userId, difficultyLevel=level,
                angleTolerance = when(level) { 1->20f; 2->17f; 3->15f; 4->12f; else->10f })
    }
}

data class ClockDrawingResult(
    override val gameId: String, override val sessionId: String,
    override val metrics: MetricsSnapshot, override val difficultyReached: Int,
    val targetTime: ClockTime,
    val hourAngleError: Float,   // grados de error
    val minuteAngleError: Float,
    val hourCorrect: Boolean,
    val minuteCorrect: Boolean,
    val totalTimeSeconds: Int,
    val corrections: Int
) : GameResult

data class ClockDrawingUiState(
    val targetTime: ClockTime              = ClockTime(10, 10),
    val phase: ClockPhase                  = ClockPhase.DRAW_HOUR,
    val hourHandAngle: Float?              = null,   // null = no dibujada aún
    val minuteHandAngle: Float?            = null,
    val corrections: Int                   = 0,
    val evaluationResult: Pair<Boolean, Boolean>? = null   // (hourOK, minuteOK)
)

// ── Engine ────────────────────────────────────────────────────────────────────

class ClockDrawingEngine(override val config: ClockDrawingConfig) :
    GameEngine<ClockDrawingConfig, ClockDrawingResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())
    private val _uiState = MutableStateFlow(ClockDrawingUiState(targetTime = CLOCK_TARGETS.random()))
    val uiState: StateFlow<ClockDrawingUiState> = _uiState.asStateFlow()
    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private var startMs     = 0L
    private var corrections = 0
    private var finalResult: ClockDrawingResult? = null

    override fun start() { _state.value = GameState.Instructions }

    fun begin() {
        startMs = SystemTimeProvider().uptimeMillis()
        _state.value = GameState.Playing(0f, null)
        metrics.recordStimulusShown()
    }

    /**
     * Recibe un toque en coordenadas normalizadas (0..1) y calcula el ángulo
     * de la manecilla correspondiente al estado actual.
     * @param normalizedPos posición tocada en coordenadas 0..1 del canvas del reloj.
     */
    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing) return InputFeedback.Ignored
        if (input !is UserInput.TapTarget) return InputFeedback.Ignored
        val parts = input.targetId.split(",")
        val x = parts.getOrNull(0)?.toFloatOrNull() ?: return InputFeedback.Ignored
        val y = parts.getOrNull(1)?.toFloatOrNull() ?: return InputFeedback.Ignored

        val angle = computeAngleFromCenter(x, y)
        val ui    = _uiState.value

        return when (ui.phase) {
            ClockPhase.DRAW_HOUR -> {
                // Si ya había una → corrección
                if (ui.hourHandAngle != null) corrections++
                metrics.recordUserResponse(isCorrect = true)
                _uiState.value = ui.copy(phase = ClockPhase.DRAW_MINUTE, hourHandAngle = angle)
                _state.value   = GameState.Playing(0.5f, null)
                InputFeedback.Accepted
            }
            ClockPhase.DRAW_MINUTE -> {
                if (ui.minuteHandAngle != null) corrections++
                metrics.recordUserResponse(isCorrect = true)
                val newUi = ui.copy(phase = ClockPhase.DONE, minuteHandAngle = angle, corrections = corrections)
                _uiState.value = newUi
                scope.launch { evaluate(newUi) }
                InputFeedback.Accepted
            }
            ClockPhase.DONE -> InputFeedback.Ignored
        }
    }

    /** Permite resetear una manecilla específica. */
    fun resetHand(hand: ClockPhase) {
        if (hand == ClockPhase.DRAW_HOUR) {
            corrections++
            _uiState.value = _uiState.value.copy(
                phase = ClockPhase.DRAW_HOUR, hourHandAngle = null, minuteHandAngle = null
            )
        } else if (hand == ClockPhase.DRAW_MINUTE) {
            corrections++
            _uiState.value = _uiState.value.copy(phase = ClockPhase.DRAW_MINUTE, minuteHandAngle = null)
        }
    }

    private suspend fun evaluate(ui: ClockDrawingUiState) {
        val target   = ui.targetTime
        val hourErr  = angularError(ui.hourHandAngle ?: 0f, target.expectedHourAngle)
        val minErr   = angularError(ui.minuteHandAngle ?: 0f, target.expectedMinuteAngle)
        val hourOK   = hourErr <= config.angleTolerance
        val minOK    = minErr  <= config.angleTolerance

        _uiState.value = ui.copy(evaluationResult = hourOK to minOK)
        delay(1200L)

        val elapsed = ((SystemTimeProvider().uptimeMillis() - startMs) / 1000).toInt()
        val snap    = metrics.getMetrics()
        finalResult = ClockDrawingResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel, targetTime=target,
            hourAngleError=hourErr, minuteAngleError=minErr,
            hourCorrect=hourOK, minuteCorrect=minOK,
            totalTimeSeconds=elapsed, corrections=corrections
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    /** Ángulo (0..360°) desde las 12 en sentido horario dado un punto normalizado. */
    internal fun computeAngleFromCenter(x: Float, y: Float): Float {
        val dx = x - 0.5f; val dy = y - 0.5f
        val rad = atan2(dx.toDouble(), (-dy).toDouble())
        return ((rad * 180.0 / PI + 360) % 360).toFloat()
    }

    /** Diferencia angular mínima entre dos ángulos (0..180°). */
    internal fun angularError(actual: Float, expected: Float): Float {
        val diff = ((actual - expected + 540) % 360 - 180).absoluteValue
        return diff
    }

    override fun pause()  {}; override fun resume() {}
    override fun forceEnd(): ClockDrawingResult = finalResult ?: runBlocking { evaluate(_uiState.value); finalResult!! }
}
