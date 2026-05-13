package com.eternamente.app.presentation.games.trailmaking

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.games.engine.*
import com.eternamente.app.ui.theme.EternaMenteTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope

@HiltViewModel
class TrailMakingViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<TrailMakingConfig, TrailMakingResult>(save, update, session, prefs) {
    private var _engine: TrailMakingEngine? = null
    override val engine: GameEngine<TrailMakingConfig, TrailMakingResult> get() = requireNotNull(_engine)
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(TrailMakingUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<TrailMakingUiState> = _uiState.asStateFlow()

    fun initialize(config: TrailMakingConfig) {
        if (_engine != null) return
        val e = TrailMakingEngine(config)
        _engine = e
        viewModelScope.launch { e.uiState.collect { _uiState.value = it } }
    }
    fun startCountdown() = _engine?.startCountdown()
    fun checkProximity(norm: Offset) = _engine?.checkProximityAndConnect(norm) ?: false
    fun updatePath(path: List<Offset>) = _engine?.updateTouchPath(path)

    override fun buildDomainResult(r: TrailMakingResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.EXECUTIVE,
        scoreRaw = r.completedNodes.toFloat(),
        scoreNormalized = run {
            val comp = r.completedNodes.toFloat() / r.totalNodes
            val time = (r.metrics.mean / 2000f).coerceIn(0f, 1f)
            val err  = 1f - (r.sequenceErrors.toFloat() / 10f).coerceIn(0f, 1f)
            (comp * 70f + err * 20f + time * 10f).coerceIn(0f, 100f)
        },
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun TrailMakingScreen(
    innerPadding: PaddingValues, sessionId: String, userId: String,
    difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit
) {
    val viewModel: TrailMakingViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(TrailMakingUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(TrailMakingConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { e ->
            if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score)
        }
    }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (val state = gameState) {
            GameState.Instructions  -> TrailInstructions(difficultyLevel >= 3) { viewModel.startCountdown() }
            is GameState.Countdown  -> TrailCountdown(state.seconds)
            is GameState.Playing    -> TrailPlayArea(uiState, onProximity = { norm ->
                val hit = viewModel.checkProximity(norm)
                if (hit) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }, onPathUpdated = { viewModel.updatePath(it) })
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TrailPlayArea(uiState: TrailMakingUiState, onProximity: (Offset) -> Unit, onPathUpdated: (List<Offset>) -> Unit) {
    var touchPoints by remember { mutableStateOf(listOf<Offset>()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
            Text("Siguiente: ${uiState.nodes.getOrNull(uiState.currentTarget)?.label ?: "✓"}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            Text("Errores: ${uiState.sequenceErrors}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            Text("${uiState.timeLeft}s", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (uiState.timeLeft > 30) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        touchPoints = listOf(o)
                        val norm = Offset(o.x / size.width, o.y / size.height)
                        onProximity(norm)
                    },
                    onDrag = { change, _ ->
                        touchPoints = touchPoints + change.position
                        val norm = Offset(change.position.x / size.width, change.position.y / size.height)
                        onProximity(norm)
                        onPathUpdated(touchPoints)
                    },
                    onDragEnd = { touchPoints = emptyList(); onPathUpdated(emptyList()) }
                )
            }
        ) {
            val w = maxWidth; val h = maxHeight
            val density = LocalDensity.current

            // Dibujar líneas y path en Canvas
            Canvas(Modifier.fillMaxSize()) {
                // Líneas conectadas
                val path = uiState.connectedPath
                if (path.size >= 2) {
                    for (i in 0 until path.size - 1) {
                        val a = uiState.nodes[path[i]].position
                        val b = uiState.nodes[path[i+1]].position
                        drawLine(Color(0xFF1565C0), Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), 6f, StrokeCap.Round)
                    }
                }
                // Path del dedo
                if (touchPoints.size >= 2) {
                    val fp = Path()
                    fp.moveTo(touchPoints.first().x, touchPoints.first().y)
                    touchPoints.drop(1).forEach { fp.lineTo(it.x, it.y) }
                    drawPath(fp, Color(0x661565C0), style = Stroke(4f, cap = StrokeCap.Round))
                }
            }

            // Nodos como composables
            uiState.nodes.forEach { node ->
                val xDp = w * node.position.x
                val yDp = h * node.position.y
                val nodeSize = 44.dp
                Box(
                    modifier = Modifier
                        .offset(x = xDp - nodeSize / 2, y = yDp - nodeSize / 2)
                        .size(nodeSize)
                        .background(
                            when {
                                node.isConnected -> MaterialTheme.colorScheme.secondary
                                node.isTarget    -> MaterialTheme.colorScheme.primary
                                else             -> MaterialTheme.colorScheme.surfaceVariant
                            }, CircleShape
                        )
                        .semantics { contentDescription = "Nodo ${node.label}" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(node.label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (node.isConnected || node.isTarget) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun TrailInstructions(isAlternating: Boolean, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Conecta los Puntos", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text(if (isAlternating) "Conecta en orden: 1 → A → 2 → B → 3 → C …" else "Conecta los números en orden: 1 → 2 → 3 … → 25",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("Arrastra el dedo de número en número sin levantar", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de conectar puntos")
    }
}

@Composable
private fun TrailCountdown(s: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$s", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            Text("¡Prepárate!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.85f))
        }
    }
}

@Preview(name = "Trail Instructions", showBackground = true)
@Preview(name = "Trail Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrailPreview() { EternaMenteTheme { TrailInstructions(false) {} } }
