package com.eternamente.app.presentation.games.prospectivememory

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

@HiltViewModel
class ProspectiveMemViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<ProspectiveConfig, ProspectiveResult>(save, update, session, prefs) {
    private var _engine: ProspectiveMemEngine? = null
    override val engine: GameEngine<ProspectiveConfig, ProspectiveResult> get() = requireNotNull(_engine)
    val uiState get() = _engine?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(ProspectiveUiState())
    fun initialize(c: ProspectiveConfig) { if (_engine != null) return; _engine = ProspectiveMemEngine(c) }
    fun begin() = _engine?.begin()
    fun tapCircle(): InputFeedback = onUserInput(UserInput.Tap)
    override fun buildDomainResult(r: ProspectiveResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.MEMORY,
        scoreRaw = r.hits.toFloat(),
        scoreNormalized = ((r.hits.toFloat() / r.targetAppearances * 70f) + (1f - r.falsePositives.toFloat() / 5f).coerceIn(0f, 1f) * 30f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun ProspectiveMemScreen(innerPadding: PaddingValues, sessionId: String, userId: String, difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit) {
    val viewModel: ProspectiveMemViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(ProspectiveUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) { viewModel.initialize(ProspectiveConfig.forDifficulty(difficultyLevel, sessionId, userId)); viewModel.startGame() }
    LaunchedEffect(Unit) { viewModel.navigationEvent.collect { e -> if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score) } }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (gameState) {
            GameState.Instructions -> PMInstructions(uiState.targetEmoji) { viewModel.begin() }
            is GameState.Playing   -> PMPlayArea(uiState) {
                val fb = viewModel.tapCircle()
                if (fb == InputFeedback.Correct) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                else haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PMPlayArea(ui: ProspectiveUiState, onTapCircle: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Recuerda: toca el círculo verde cuando veas ${ui.targetEmoji}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("✓ ${ui.hits}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                Text("✗ ${ui.falsePositives}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            }
        }
        LinearProgressIndicator(progress = { ui.stimulusIndex.toFloat() / ui.totalStimuli }, modifier = Modifier.fillMaxWidth().height(6.dp))
        Spacer(Modifier.weight(0.5f))

        // Estímulo actual
        if (ui.currentEmoji != null) {
            Text(ui.currentEmoji, fontSize = 80.sp, modifier = Modifier.semantics { contentDescription = "Estímulo: ${ui.currentEmoji}" })
        } else {
            Box(Modifier.size(80.dp))
        }

        Spacer(Modifier.weight(0.5f))

        // Círculo verde (botón objetivo)
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(if (ui.circleActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                .border(3.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                .clickable(role = Role.Button) { onTapCircle() }
                .semantics { contentDescription = "Círculo verde. Pulsa cuando veas ${ui.targetEmoji}"; role = Role.Button },
            contentAlignment = Alignment.Center
        ) {
            Text(if (ui.circleActive) "✓" else "", fontSize = 36.sp, color = MaterialTheme.colorScheme.onSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Text("← Toca aquí cuando veas ${ui.targetEmoji}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PMInstructions(targetEmoji: String, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Memoria Prospectiva", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Aparecerán distintos emojis uno a uno.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("RECUERDA: toca el círculo verde SOLO cuando aparezca:", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(targetEmoji, fontSize = 72.sp, modifier = Modifier.semantics { contentDescription = "Emoji objetivo: $targetEmoji" })
        Text("Para todo lo demás, NO toques el círculo.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de memoria prospectiva")
    }
}

@Preview(showBackground = true) @Composable private fun PMPreview() { EternaMenteTheme { PMInstructions("🐦") {} } }
