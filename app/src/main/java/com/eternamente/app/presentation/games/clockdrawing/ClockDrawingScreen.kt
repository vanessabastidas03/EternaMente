package com.eternamente.app.presentation.games.clockdrawing

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.usecase.SaveGameResultUseCase
import com.eternamente.app.domain.usecase.UpdateGamificationUseCase
import com.eternamente.app.presentation.component.EternaButton
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.games.engine.*
import com.eternamente.app.ui.theme.EternaMenteTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.*

@HiltViewModel
class ClockDrawingViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<ClockDrawingConfig, ClockDrawingResult>(save, update, session, prefs) {
    private var _engine: ClockDrawingEngine? = null
    override val engine: GameEngine<ClockDrawingConfig, ClockDrawingResult> get() = requireNotNull(_engine)
    val uiState get() = _engine?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(ClockDrawingUiState())

    fun initialize(c: ClockDrawingConfig) { if (_engine != null) return; _engine = ClockDrawingEngine(c) }
    fun begin() = _engine?.begin()
    fun tapClock(nx: Float, ny: Float): InputFeedback = onUserInput(UserInput.TapTarget("$nx,$ny"))
    fun resetHand(phase: ClockPhase) = _engine?.resetHand(phase)

    override fun buildDomainResult(r: ClockDrawingResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.EXECUTIVE,
        scoreRaw = (if (r.hourCorrect) 50f else 0f) + (if (r.minuteCorrect) 50f else 0f),
        scoreNormalized = run {
            val angularScore = 100f - ((r.hourAngleError + r.minuteAngleError) / 2f).coerceIn(0f, 100f)
            val correctionPenalty = (r.corrections * 5f).coerceIn(0f, 30f)
            (angularScore - correctionPenalty).coerceIn(0f, 100f)
        },
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = if (r.hourCorrect && r.minuteCorrect) 100f else if (r.hourCorrect || r.minuteCorrect) 50f else 0f,
        errorsCount = r.corrections, difficultyLevel = r.difficultyReached
    )
}

@Composable
fun ClockDrawingScreen(
    innerPadding: PaddingValues, sessionId: String, userId: String,
    difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit
) {
    val viewModel: ClockDrawingViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(ClockDrawingUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(ClockDrawingConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { e ->
            if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score)
        }
    }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (gameState) {
            GameState.Instructions -> ClockInstructions { viewModel.begin() }
            is GameState.Playing   -> ClockPlayArea(uiState,
                onTap = { nx, ny ->
                    viewModel.tapClock(nx, ny)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onResetHour   = { viewModel.resetHand(ClockPhase.DRAW_HOUR) },
                onResetMinute = { viewModel.resetHand(ClockPhase.DRAW_MINUTE) }
            )
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ClockPlayArea(
    ui: ClockDrawingUiState, onTap: (Float, Float) -> Unit,
    onResetHour: () -> Unit, onResetMinute: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Dibuja las manecillas para las", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(ui.targetTime.label, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = colors.primary)
        Spacer(Modifier.height(8.dp))

        // Instrucción de la fase
        val phaseText = when (ui.phase) {
            ClockPhase.DRAW_HOUR   -> "1. Toca donde van las HORAS"
            ClockPhase.DRAW_MINUTE -> "2. Toca donde van los MINUTOS"
            ClockPhase.DONE        -> when (ui.evaluationResult) {
                null                  -> "Calculando…"
                true  to true         -> "✓ ¡Perfecto! Ambas correctas"
                false to true         -> "✗ Hora incorrecta, Minutos bien"
                true  to false        -> "✓ Hora bien, Minutos incorrectos"
                else                  -> "✗ Las dos manecillas necesitan corrección"
            }
        }
        Surface(shape = RoundedCornerShape(8.dp), color = when (ui.phase) {
            ClockPhase.DRAW_HOUR   -> colors.primaryContainer
            ClockPhase.DRAW_MINUTE -> colors.secondaryContainer
            ClockPhase.DONE        -> if (ui.evaluationResult == true to true) colors.secondaryContainer else colors.errorContainer
        }) {
            Text(phaseText, modifier = Modifier.padding(12.dp, 8.dp), style = MaterialTheme.typography.titleMedium, color = colors.onBackground, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(16.dp))

        // Canvas del reloj
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val clockSize = minOf(maxWidth, 300.dp)
            Canvas(
                modifier = Modifier
                    .size(clockSize)
                    .semantics { contentDescription = "Reloj. Toca para colocar la manecilla" }
                    .then(if (ui.phase != ClockPhase.DONE) Modifier.pointerInput(ui.phase) {
                        detectTapGestures { offset ->
                            val nx = offset.x / size.width
                            val ny = offset.y / size.height
                            onTap(nx, ny)
                        }
                    } else Modifier)
            ) {
                drawClockFace(colors.outline, colors.onBackground)
                // Manecilla de horas
                ui.hourHandAngle?.let { angle ->
                    val handColor = if (ui.evaluationResult?.first == false) Color(0xFFE53935)
                                    else if (ui.evaluationResult?.first == true) Color(0xFF43A047)
                                    else colors.primary
                    drawHand(angle, 0.45f, 7f, handColor)
                }
                // Manecilla de minutos
                ui.minuteHandAngle?.let { angle ->
                    val handColor = if (ui.evaluationResult?.second == false) Color(0xFFE53935)
                                    else if (ui.evaluationResult?.second == true) Color(0xFF43A047)
                                    else colors.secondary
                    drawHand(angle, 0.62f, 4f, handColor)
                }
                // Centro
                drawCircle(colors.onBackground, 8f)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Botones de corrección
        if (ui.phase == ClockPhase.DRAW_MINUTE && ui.hourHandAngle != null) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResetHour, modifier = Modifier.weight(1f).semantics { contentDescription = "Repetir manecilla de horas" }) {
                    Text("↩ Corregir Horas")
                }
                Text("", modifier = Modifier.weight(1f))
            }
        }
        if (ui.corrections > 0) {
            Text("Correcciones: ${ui.corrections}", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }
    }
}

private fun DrawScope.drawClockFace(outlineColor: Color, numbersColor: Color) {
    val cx = size.width / 2; val cy = size.height / 2; val r = size.minDimension / 2 - 4f
    drawCircle(outlineColor, r, Offset(cx, cy), style = Stroke(3f))
    for (i in 1..12) {
        val angle = (i * 30 - 90) * PI / 180
        val mx = (cx + r * 0.82f * cos(angle)).toFloat()
        val my = (cy + r * 0.82f * sin(angle)).toFloat()
        drawCircle(numbersColor, 3f, Offset(mx, my))
    }
}

private fun DrawScope.drawHand(angleDeg: Float, length: Float, strokeWidth: Float, color: Color) {
    val cx = size.width / 2; val cy = size.height / 2; val r = size.minDimension / 2
    val rad = (angleDeg - 90) * PI / 180
    val ex = (cx + r * length * cos(rad)).toFloat()
    val ey = (cy + r * length * sin(rad)).toFloat()
    drawLine(color, Offset(cx, cy), Offset(ex, ey), strokeWidth, cap = StrokeCap.Round)
}

@Composable
private fun ClockInstructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Reloj en Punto", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Se mostrará una hora. Toca en el reloj para colocar primero la manecilla de las HORAS y luego la de los MINUTOS.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("Ejemplo: Para las 3:30 — la aguja corta apunta a las 3, la larga a las 6.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de reloj")
    }
}

@Preview(showBackground = true) @Composable private fun ClockPreview() { EternaMenteTheme { ClockInstructions {} } }
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES) @Composable private fun ClockDarkPreview() { EternaMenteTheme { ClockInstructions {} } }
