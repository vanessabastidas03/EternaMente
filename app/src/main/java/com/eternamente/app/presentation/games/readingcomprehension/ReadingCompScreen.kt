package com.eternamente.app.presentation.games.readingcomprehension

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
class ReadingCompViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<ReadingConfig, ReadingResult>(save, update, session, prefs) {
    private var _engine: ReadingCompEngine? = null
    override val engine: GameEngine<ReadingConfig, ReadingResult> get() = requireNotNull(_engine)
    val uiState get() = _engine?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(ReadingUiState())
    fun initialize(c: ReadingConfig) { if (_engine != null) return; _engine = ReadingCompEngine(c) }
    fun beginReading() = _engine?.beginReading()
    fun proceedToQuestions() = _engine?.proceedToQuestions()
    fun selectAnswer(idx: Int): InputFeedback = onUserInput(UserInput.SelectOption(idx))
    override fun buildDomainResult(r: ReadingResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.LANGUAGE,
        scoreRaw = r.correctAnswers.toFloat(),
        scoreNormalized = (r.correctAnswers.toFloat() / r.totalQuestions * 100f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun ReadingCompScreen(innerPadding: PaddingValues, sessionId: String, userId: String, difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit) {
    val viewModel: ReadingCompViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(ReadingUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) { viewModel.initialize(ReadingConfig.forDifficulty(difficultyLevel, sessionId, userId)); viewModel.startGame() }
    LaunchedEffect(Unit) { viewModel.navigationEvent.collect { e -> if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score) } }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (gameState) {
            GameState.Instructions -> RCInstructions { viewModel.beginReading() }
            is GameState.Playing   -> when (uiState.phase) {
                ReadingPhase.READING    -> RCReadingPhase(uiState) { viewModel.proceedToQuestions() }
                ReadingPhase.QUESTIONS  -> RCQuestionsPhase(uiState) { idx ->
                    val fb = viewModel.selectAnswer(idx)
                    if (fb == InputFeedback.Correct) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RCReadingPhase(ui: ReadingUiState, onReady: () -> Unit) {
    val passage = ui.passage ?: return
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(passage.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(passage.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).semantics { contentDescription = "Texto: ${passage.text}" })
        Spacer(Modifier.height(16.dp))
        EternaFullWidthButton("He terminado de leer", onReady, contentDescription = "Continuar a las preguntas de comprensión")
    }
}

@Composable
private fun RCQuestionsPhase(ui: ReadingUiState, onSelect: (Int) -> Unit) {
    val passage = ui.passage ?: return
    val q       = passage.questions.getOrNull(ui.currentQuestion) ?: return

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LinearProgressIndicator(progress = { (ui.currentQuestion + 1).toFloat() / passage.questions.size }, modifier = Modifier.fillMaxWidth().height(6.dp))
        Spacer(Modifier.height(8.dp))
        Text("Pregunta ${ui.currentQuestion + 1} de ${passage.questions.size}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Mostrar texto si está habilitado
        if (ui.showText) {
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(passage.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()).heightIn(max = 120.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(q.question, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))

        q.options.forEachIndexed { idx, opt ->
            val isSelected  = ui.selectedAnswer == idx
            val isCorrect   = idx == q.correctIndex && ui.selectedAnswer != null
            val isWrong     = isSelected && ui.isCorrect == false
            Surface(
                onClick = { if (ui.selectedAnswer == null) onSelect(idx) },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 3.dp).semantics { contentDescription = "Opción: $opt" },
                shape = RoundedCornerShape(12.dp),
                color = when { isCorrect -> MaterialTheme.colorScheme.secondaryContainer; isWrong -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceVariant },
                tonalElevation = 2.dp
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(opt, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun RCInstructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Lectura y Comprensión", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Leerás un texto corto. Después responderás 3 preguntas de comprensión.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("Tómate el tiempo que necesites para leer.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar lectura!", onStart, contentDescription = "Comenzar lectura")
    }
}

@Preview(showBackground = true) @Composable private fun RCPreview() { EternaMenteTheme { RCInstructions {} } }
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES) @Composable private fun RCDarkPreview() { EternaMenteTheme { RCInstructions {} } }
