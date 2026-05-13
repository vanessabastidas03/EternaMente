package com.eternamente.app.presentation.games.stroop

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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

@HiltViewModel
class StroopViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<StroopConfig, StroopResult>(save, update, session, prefs) {
    private var _engine: StroopEngine? = null
    override val engine: GameEngine<StroopConfig, StroopResult> get() = requireNotNull(_engine)
    val uiState get() = _engine?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(StroopUiState())

    fun initialize(c: StroopConfig) { if (_engine != null) return; _engine = StroopEngine(c) }
    fun startCountdown() = _engine?.startCountdown()
    fun selectColor(idx: Int): InputFeedback = onUserInput(UserInput.SelectOption(idx))

    override fun buildDomainResult(r: StroopResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.EXECUTIVE,
        scoreRaw = r.correctCount.toFloat(),
        scoreNormalized = (r.correctCount.toFloat() / r.totalStimuli * 70f + (1f - r.interferenceIndex.coerceIn(0f, 1f)) * 30f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun StroopScreen(
    innerPadding: PaddingValues, sessionId: String, userId: String,
    difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit
) {
    val viewModel: StroopViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(StroopUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(StroopConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { e ->
            if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score)
        }
    }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        AnimatedContent(gameState, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "stroop") { state ->
            when (state) {
                GameState.Instructions -> StroopInstructions { viewModel.startCountdown() }
                is GameState.Countdown -> StroopCountdown(state.seconds)
                is GameState.Playing   -> StroopPlayArea(uiState) { colorIdx ->
                    val fb = viewModel.selectColor(colorIdx)
                    if (fb == InputFeedback.Correct) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StroopPlayArea(ui: StroopUiState, onSelect: (Int) -> Unit) {
    val s = ui.stimulus
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${ui.stimulusIndex+1}/${ui.totalStimuli}  ✓${ui.correctCount}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("Pulsa el COLOR DE LA TINTA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        if (s != null) {
            Text(
                text  = s.wordText,
                color = s.wordColor.color,
                fontSize = 56.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "Palabra: ${s.wordText}, escrita en color diferente. Pulsa el color de la tinta." }
            )
        }
        Spacer(Modifier.weight(1f))
        // 2×2 botones de colores
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InkColor.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { ink ->
                        Button(
                            onClick = { onSelect(ink.ordinal) },
                            modifier = Modifier.weight(1f).height(64.dp).semantics { contentDescription = "Botón ${ink.label}" },
                            colors   = ButtonDefaults.buttonColors(containerColor = ink.color),
                            shape    = RoundedCornerShape(12.dp)
                        ) { Text(ink.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StroopInstructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Stroop de Colores", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Verás una palabra de color escrita en tinta de OTRO color.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        // Demo
        Text("ROJO", color = InkColor.BLUE.color, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text("← Tinta azul. ¡Pulsa AZUL!", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Ignora la palabra. Responde al COLOR DE LA TINTA.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego Stroop")
    }
}

@Composable private fun StroopCountdown(s: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$s", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            Text("¡Prepárate!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.85f))
        }
    }
}

@Preview(showBackground = true) @Composable private fun StroopPreview() { EternaMenteTheme { StroopInstructions {} } }
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES) @Composable private fun StroopDarkPreview() { EternaMenteTheme { StroopInstructions {} } }
