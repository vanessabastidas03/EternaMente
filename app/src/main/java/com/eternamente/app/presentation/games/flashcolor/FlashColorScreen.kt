package com.eternamente.app.presentation.games.flashcolor

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
class FlashColorViewModel @Inject constructor(
    saveGameResultUseCase: SaveGameResultUseCase,
    updateGamificationUseCase: UpdateGamificationUseCase,
    sessionRepository: SessionRepository,
    userPreferencesRepository: UserPreferencesRepository
) : GameBaseViewModel<FlashColorConfig, FlashColorResult>(saveGameResultUseCase, updateGamificationUseCase, sessionRepository, userPreferencesRepository) {
    private var _engine: FlashColorEngine? = null
    override val engine: GameEngine<FlashColorConfig, FlashColorResult> get() = requireNotNull(_engine)
    private val _ui = androidx.lifecycle.MutableLiveData(FlashColorUiState())
    val uiState get() = _engine?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(FlashColorUiState()).asStateFlow()

    fun initialize(config: FlashColorConfig) {
        if (_engine != null) return
        _engine = FlashColorEngine(config)
    }
    fun startCountdown() = _engine?.startCountdown()
    fun tap(): InputFeedback = onUserInput(UserInput.Tap)
    override fun buildDomainResult(engineResult: FlashColorResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = engineResult.sessionId,
        gameId = engineResult.gameId, domain = com.eternamente.app.domain.model.CognitiveDomain.ATTENTION,
        scoreRaw = engineResult.hits.toFloat(),
        scoreNormalized = ((engineResult.hitRate * 60f + (1f - engineResult.faRate) * 25f + engineResult.dPrime.coerceIn(0f, 3f) / 3f * 15f)).coerceIn(0f, 100f),
        reactionTimeMsAvg = engineResult.metrics.mean, reactionTimeMsP50 = engineResult.metrics.median,
        accuracyPct = engineResult.metrics.accuracyPct, errorsCount = engineResult.metrics.errorCount,
        difficultyLevel = engineResult.difficultyReached
    )
    private fun <T> kotlinx.coroutines.flow.MutableStateFlow<T>.asStateFlow() = this as kotlinx.coroutines.flow.StateFlow<T>
}

@Composable
fun FlashColorScreen(
    innerPadding: PaddingValues, sessionId: String, userId: String,
    difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit
) {
    val viewModel: FlashColorViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   = viewModel.uiState.collectAsState(FlashColorUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(FlashColorConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { e ->
            if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score)
        }
    }

    Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        AnimatedContent(gameState, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "fc") { state ->
            when (state) {
                GameState.Instructions -> FlashColorInstructions(
                    uiState.value.targetColor, onStart = { viewModel.startCountdown() }
                )
                is GameState.Countdown -> FlashCountdown(state.seconds)
                is GameState.Playing   -> FlashColorPlay(
                    uiState = uiState.value,
                    onTap   = {
                        val fb = viewModel.tap()
                        if (fb == InputFeedback.Correct) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        else if (fb == InputFeedback.Incorrect) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun FlashColorInstructions(target: StimulusColor, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Flash de Colores", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Pulsa SOLO cuando veas este color:", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Box(Modifier.size(100.dp).clip(CircleShape).background(target.color).semantics { contentDescription = "Color objetivo: ${target.displayName}" })
        Text(target.displayName, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = target.color)
        Text("No pulses cuando veas otros colores", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de colores")
    }
}

@Composable
private fun FlashColorPlay(uiState: FlashColorUiState, onTap: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Column {
                Text("Aciertos: ${uiState.hits}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary)
                Text("Errores:  ${uiState.falseAlarms}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            }
            Text("${uiState.stimulusIndex+1}/${uiState.totalStimuli}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text("Pulsa solo el: ${uiState.targetColor.displayName}", style = MaterialTheme.typography.bodyLarge, color = uiState.targetColor.color)
        Spacer(Modifier.weight(0.5f))
        // Estímulo
        Box(Modifier.size(180.dp).clip(CircleShape).background(
            if (uiState.currentColor != null) uiState.currentColor.color else MaterialTheme.colorScheme.surfaceVariant
        ).semantics { contentDescription = if (uiState.currentColor != null) "Color: ${uiState.currentColor.displayName}" else "Sin estímulo" })
        Spacer(Modifier.weight(0.5f))
        // Botón de respuesta
        Button(
            onClick = onTap, enabled = uiState.isShowingStimulus,
            modifier = Modifier.fillMaxWidth().height(80.dp).semantics { contentDescription = "Pulsar cuando veas ${uiState.targetColor.displayName}" },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = uiState.targetColor.color)
        ) { Text("¡PULSA!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FlashCountdown(s: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$s", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            Text("¡Prepárate!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.85f))
        }
    }
}

@Preview(showBackground = true) @Composable
private fun FlashInstructionsPreview() { EternaMenteTheme { FlashColorInstructions(StimulusColor.RED) {} } }
