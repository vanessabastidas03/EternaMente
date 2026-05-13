package com.eternamente.app.presentation.games.mentalcalc

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.usecase.SaveGameResultUseCase
import com.eternamente.app.domain.usecase.UpdateGamificationUseCase
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.games.digitspan.NumpadGrid
import com.eternamente.app.presentation.games.engine.*
import com.eternamente.app.ui.theme.EternaMenteTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MentalCalcViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<MentalCalcConfig, MentalCalcResult>(save, update, session, prefs) {
    private var _engine: MentalCalcEngine? = null
    override val engine: GameEngine<MentalCalcConfig, MentalCalcResult> get() = requireNotNull(_engine)
    val uiState get() = _engine?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(MentalCalcUiState())
    fun initialize(c: MentalCalcConfig) { if (_engine != null) return; _engine = MentalCalcEngine(c) }
    fun begin() = _engine?.begin()
    fun input(v: Int): InputFeedback = onUserInput(UserInput.SelectOption(v))
    override fun buildDomainResult(r: MentalCalcResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.PROCESSING_SPEED,
        scoreRaw = r.correctCount.toFloat(),
        scoreNormalized = (r.correctCount.toFloat() / r.totalProblems * 100f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun MentalCalcScreen(innerPadding: PaddingValues, sessionId: String, userId: String, difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit) {
    val viewModel: MentalCalcViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(MentalCalcUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) { viewModel.initialize(MentalCalcConfig.forDifficulty(difficultyLevel, sessionId, userId)); viewModel.startGame() }
    LaunchedEffect(Unit) { viewModel.navigationEvent.collect { e -> if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score) } }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (val state = gameState) {
            GameState.Instructions  -> MCInstructions { viewModel.begin() }
            is GameState.Countdown  -> MCCountdown(state.seconds)
            is GameState.Playing    -> MCPlayArea(uiState,
                onDigit   = { d -> viewModel.input(d); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                onDelete  = { viewModel.input(-1) },
                onConfirm = { viewModel.input(-2) }
            )
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MCPlayArea(ui: MentalCalcUiState, onDigit: (Int) -> Unit, onDelete: () -> Unit, onConfirm: () -> Unit) {
    val p = ui.problem ?: return
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("${ui.problemIndex+1}/${ui.totalProblems}", style = MaterialTheme.typography.titleMedium)
            Text("${(ui.timeRemainingMs / 1000).toInt()}s", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (ui.timeRemainingMs > 3000) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        LinearProgressIndicator(progress = { ui.timeRemainingMs.toFloat() / 10_000f }, modifier = Modifier.fillMaxWidth().height(6.dp))
        Spacer(Modifier.weight(0.5f))

        // Problema
        Text(p.text, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = "Problema: ${p.text}" })
        Spacer(Modifier.height(16.dp))

        // Respuesta
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = when(ui.lastWasCorrect) { true -> MaterialTheme.colorScheme.secondaryContainer; false -> MaterialTheme.colorScheme.errorContainer; null -> MaterialTheme.colorScheme.surfaceVariant }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(ui.currentInput.ifEmpty { "…" }, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { contentDescription = "Tu respuesta: ${ui.currentInput.ifEmpty { "vacío" }}" })
            }
        }

        Spacer(Modifier.weight(0.5f))

        // Numpad reutilizado del DigitSpan
        NumpadGrid(onDigitTap = onDigit, onDelete = onDelete)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = "Confirmar respuesta" }) {
            Text("Confirmar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun MCInstructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Cálculo Mental", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Resuelve 10 operaciones aritméticas lo más rápido posible. Tienes 10 segundos por operación.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar cálculo mental")
    }
}

@Composable private fun MCCountdown(s: Int) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$s", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary); Text("¡Prepárate!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.85f)) } } }

@Preview(showBackground = true) @Composable private fun MCPreview() { EternaMenteTheme { MCInstructions {} } }
