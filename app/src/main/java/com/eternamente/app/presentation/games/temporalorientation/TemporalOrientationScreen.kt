package com.eternamente.app.presentation.games.temporalorientation

import android.content.res.Configuration
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
class TemporalOrientationViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<TemporalConfig, TemporalResult>(save, update, session, prefs) {
    private var _engine: TemporalOrientationEngine? = null
    override val engine: GameEngine<TemporalConfig, TemporalResult> get() = requireNotNull(_engine)
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(TemporalUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<TemporalUiState> = _uiState.asStateFlow()

    fun initialize(c: TemporalConfig) {
        if (_engine != null) return
        val e = TemporalOrientationEngine(c)
        _engine = e
        viewModelScope.launch { e.uiState.collect { _uiState.value = it } }
    }
    fun begin() = _engine?.begin()
    fun selectAnswer(ans: String): InputFeedback = onUserInput(UserInput.TapTarget(ans))

    override fun buildDomainResult(r: TemporalResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.ORIENTATION,
        scoreRaw = r.correctAnswers.toFloat(),
        scoreNormalized = (r.correctAnswers.toFloat() / r.totalQuestions * 100f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun TemporalOrientationScreen(
    innerPadding: PaddingValues, sessionId: String, userId: String,
    difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit
) {
    val viewModel: TemporalOrientationViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(TemporalUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(TemporalConfig(sessionId=sessionId, userId=userId, difficultyLevel=difficultyLevel))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { e ->
            if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score)
        }
    }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (gameState) {
            GameState.Instructions -> TOInstructions { viewModel.begin() }
            is GameState.Playing   -> TOPlayArea(uiState) { ans ->
                val fb = viewModel.selectAnswer(ans)
                if (fb == InputFeedback.Correct) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TOPlayArea(ui: TemporalUiState, onSelect: (String) -> Unit) {
    val q = ui.questions.getOrNull(ui.currentIndex) ?: return
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { (ui.currentIndex + 1).toFloat() / ui.questions.size },
            modifier = Modifier.fillMaxWidth().height(8.dp), color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text("Pregunta ${ui.currentIndex+1} de ${ui.questions.size}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(0.3f))
        Text(q.questionText, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(0.5f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            q.options.forEach { opt ->
                val isSelected = ui.selectedAnswer == opt
                val isCorrect  = ui.selectedAnswer != null && opt == q.correctAnswer
                val isWrong    = isSelected && ui.isCorrect == false
                val bg = when {
                    isCorrect -> MaterialTheme.colorScheme.secondaryContainer
                    isWrong   -> MaterialTheme.colorScheme.errorContainer
                    else      -> MaterialTheme.colorScheme.surfaceVariant
                }
                Surface(
                    onClick = { if (ui.selectedAnswer == null) onSelect(opt) },
                    modifier = Modifier.fillMaxWidth().height(60.dp).semantics { contentDescription = "Opción: $opt" },
                    shape = RoundedCornerShape(12.dp), color = bg, tonalElevation = 2.dp
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(opt, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
        Spacer(Modifier.weight(0.2f))
    }
}

@Composable
private fun TOInstructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Orientación Temporal", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Responde 5 preguntas sencillas sobre la fecha y hora de hoy.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("Selecciona la respuesta correcta de las 4 opciones.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de orientación temporal")
    }
}

@Preview(showBackground = true) @Composable private fun TOPreview() { EternaMenteTheme { TOInstructions {} } }
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES) @Composable private fun TODarkPreview() { EternaMenteTheme { TOInstructions {} } }
