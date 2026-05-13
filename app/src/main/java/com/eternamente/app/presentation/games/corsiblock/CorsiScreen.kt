package com.eternamente.app.presentation.games.corsiblock

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

@HiltViewModel
class CorsiViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<CorsiConfig, CorsiResult>(save, update, session, prefs) {
    private var _engine: CorsiEngine? = null
    override val engine: GameEngine<CorsiConfig, CorsiResult> get() = requireNotNull(_engine)
    val uiState get() = _engine?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(CorsiUiState())

    fun initialize(c: CorsiConfig) { if (_engine != null) return; _engine = CorsiEngine(c) }
    fun startCountdown() = _engine?.startCountdown()
    fun tapBlock(idx: Int): InputFeedback = onUserInput(UserInput.SelectOption(idx))

    override fun buildDomainResult(r: CorsiResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.MEMORY,
        scoreRaw = r.maxCorrectSpan.toFloat(),
        scoreNormalized = (r.correctRounds.toFloat() / r.totalRounds * 70f + r.maxCorrectSpan.toFloat() / 7f * 30f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun CorsiScreen(
    innerPadding: PaddingValues, sessionId: String, userId: String,
    difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit
) {
    val viewModel: CorsiViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(CorsiUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(CorsiConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { e ->
            if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score)
        }
    }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (val state = gameState) {
            GameState.Instructions -> CorsiInstructions { viewModel.startCountdown() }
            is GameState.Countdown -> CorsiCountdown(state.seconds)
            is GameState.Playing   -> CorsiPlayArea(uiState) { idx ->
                val fb = viewModel.tapBlock(idx)
                if (fb != InputFeedback.Ignored) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CorsiPlayArea(ui: CorsiUiState, onTap: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Ronda ${ui.roundIndex+1}/5", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text  = when (ui.phase) { CorsiPhase.SHOWING -> "Observa…"; CorsiPhase.INPUT -> "¡Repite!"; CorsiPhase.FEEDBACK -> if (ui.lastWasCorrect==true) "✓ ¡Correcto!" else "✗ Incorrecto" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = when (ui.phase) { CorsiPhase.FEEDBACK -> if (ui.lastWasCorrect==true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("${ui.userInput.size}/${ui.sequenceLength} bloques", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth; val h = maxHeight
            val blockSize = 56.dp

            BLOCK_POSITIONS.forEachIndexed { idx, pos ->
                val isHighlighted = ui.highlightedBlock == idx
                val isTapped      = idx in ui.userInput
                Box(
                    modifier = Modifier
                        .offset(x = w * pos.x - blockSize / 2, y = h * pos.y - blockSize / 2)
                        .size(blockSize)
                        .background(
                            when {
                                isHighlighted -> MaterialTheme.colorScheme.primary
                                isTapped      -> MaterialTheme.colorScheme.secondary
                                else          -> MaterialTheme.colorScheme.surfaceVariant
                            }, RoundedCornerShape(8.dp)
                        )
                        .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .then(if (ui.phase == CorsiPhase.INPUT) Modifier.clickable { onTap(idx) } else Modifier)
                        .semantics { contentDescription = "Bloque ${idx+1}${if (isHighlighted) ", iluminado" else ""}${if (isTapped) ", seleccionado" else ""}" }
                )
            }
        }
    }
}

@Composable
private fun CorsiInstructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Reproduce el Patrón", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Los cuadros se iluminarán en un orden. Tócalos EN EL MISMO ORDEN cuando se apaguen.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de bloques de Corsi")
    }
}

@Composable private fun CorsiCountdown(s: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$s", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            Text("¡Prepárate!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.85f))
        }
    }
}

@Preview(showBackground = true) @Composable private fun CorsiPreview() { EternaMenteTheme { CorsiInstructions {} } }
