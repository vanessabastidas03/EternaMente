package com.eternamente.app.presentation.games.spotdiff

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
class SpotDiffViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<SpotDiffConfig, SpotDiffResult>(save, update, session, prefs) {
    private var _engine: SpotDiffEngine? = null
    override val engine: GameEngine<SpotDiffConfig, SpotDiffResult> get() = requireNotNull(_engine)
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(SpotDiffUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<SpotDiffUiState> = _uiState.asStateFlow()

    fun initialize(c: SpotDiffConfig) {
        if (_engine != null) return
        val e = SpotDiffEngine(c)
        _engine = e
        viewModelScope.launch { e.uiState.collect { _uiState.value = it } }
    }
    fun startCountdown() = _engine?.startCountdown()
    fun tapCell(row: Int, col: Int): InputFeedback = onUserInput(UserInput.TapTarget("$row,$col"))

    override fun buildDomainResult(r: SpotDiffResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.ATTENTION,
        scoreRaw = r.foundCount.toFloat(),
        scoreNormalized = (r.foundCount.toFloat() / r.totalDifferences * 80f + (1f - r.falseTaps.toFloat() / 10f).coerceIn(0f,1f) * 20f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun SpotDiffScreen(
    innerPadding: PaddingValues, sessionId: String, userId: String,
    difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit
) {
    val viewModel: SpotDiffViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(SpotDiffUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(SpotDiffConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { e ->
            if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score)
        }
    }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (val state = gameState) {
            GameState.Instructions -> SDInstructions { viewModel.startCountdown() }
            is GameState.Countdown -> SDCountdown(state.seconds)
            is GameState.Playing   -> SDPlayArea(uiState) { row, col ->
                val fb = viewModel.tapCell(row, col)
                if (fb == InputFeedback.Correct) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                else haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SDPlayArea(ui: SpotDiffUiState, onTap: (Int, Int) -> Unit) {
    val pair = ui.pair ?: return
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Encontradas: ${ui.foundPositions.size}/${pair.diffPositions.size}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary)
            Text("${ui.timeLeft}s", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (ui.timeLeft > 60) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Text("Toca las diferencias en la imagen derecha", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Imagen izquierda (referencia)
            ImageGrid(grid = pair.leftGrid, foundPos = emptySet(), isRight = false, onTap = { _, _ -> })
            // Imagen derecha (con diferencias)
            ImageGrid(grid = pair.rightGrid, foundPos = ui.foundPositions, isRight = true, onTap = onTap)
        }
    }
}

@Composable
private fun RowScope.ImageGrid(grid: List<List<String>>, foundPos: Set<Pair<Int,Int>>, isRight: Boolean, onTap: (Int,Int) -> Unit) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        grid.forEachIndexed { row, rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                rowItems.forEachIndexed { col, emoji ->
                    val isFound = isRight && (row to col) in foundPos
                    Box(
                        modifier = Modifier
                            .weight(1f).aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isFound) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .border(if (isFound) 2.dp else 0.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
                            .then(if (isRight && !isFound) Modifier.clickable(role = Role.Button) { onTap(row, col) } else Modifier)
                            .semantics { contentDescription = "Celda $emoji ${if (isFound) "encontrada" else ""}"; role = Role.Button },
                        contentAlignment = Alignment.Center
                    ) { Text(emoji, fontSize = 18.sp) }
                }
            }
        }
    }
}

@Composable
private fun SDInstructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Encuentra las Diferencias", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Hay dos imágenes casi idénticas. Toca en la imagen DERECHA donde hay diferencias.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("Encuentra todas las diferencias antes de que se acabe el tiempo.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de diferencias")
    }
}

@Composable private fun SDCountdown(s: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$s", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            Text("¡Prepárate!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.85f))
        }
    }
}

@Preview(showBackground = true) @Composable private fun SDPreview() { EternaMenteTheme { SDInstructions {} } }
