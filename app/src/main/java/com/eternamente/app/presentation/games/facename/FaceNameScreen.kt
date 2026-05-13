package com.eternamente.app.presentation.games.facename

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
class FaceNameViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<FaceNameConfig, FaceNameResult>(save, update, session, prefs) {
    private var _engine: FaceNameEngine? = null
    override val engine: GameEngine<FaceNameConfig, FaceNameResult> get() = requireNotNull(_engine)
    val uiState get() = _engine?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(FaceNameUiState())
    fun initialize(c: FaceNameConfig) { if (_engine != null) return; _engine = FaceNameEngine(c) }
    fun begin() = _engine?.begin()
    fun selectName(name: String): InputFeedback = onUserInput(UserInput.TapTarget(name))
    override fun buildDomainResult(r: FaceNameResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.MEMORY,
        scoreRaw = r.correctRecalls.toFloat(),
        scoreNormalized = (r.correctRecalls.toFloat() / r.totalPairs * 100f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun FaceNameScreen(innerPadding: PaddingValues, sessionId: String, userId: String, difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit) {
    val viewModel: FaceNameViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(FaceNameUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(FaceNameConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) { viewModel.navigationEvent.collect { e -> if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score) } }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (gameState) {
            GameState.Instructions -> FaceNameInstructions { viewModel.begin() }
            is GameState.Playing   -> when (uiState.phase) {
                FaceNamePhase.STUDY -> FaceNameStudy(uiState)
                FaceNamePhase.TEST  -> FaceNameTest(uiState) { name ->
                    val fb = viewModel.selectName(name)
                    if (fb == InputFeedback.Correct) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FaceNameStudy(ui: FaceNameUiState) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val secondsLeft = (ui.studyTimeLeftMs / 1000).toInt()
        Text("Memoriza estas caras y nombres", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        Text("$secondsLeft segundos para estudiar", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { ui.studyTimeLeftMs.toFloat() / 30_000f }, modifier = Modifier.fillMaxWidth().height(6.dp).padding(vertical = 2.dp))
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ui.studyPairs) { person ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(person.emoji, fontSize = 48.sp, modifier = Modifier.semantics { contentDescription = person.hint })
                        Text(person.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                        Text(person.hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceNameTest(ui: FaceNameUiState, onSelect: (String) -> Unit) {
    val face = ui.currentFace ?: return
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${ui.currentTestIndex + 1}/${ui.studyPairs.size}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(face.emoji, fontSize = 80.sp, modifier = Modifier.semantics { contentDescription = "¿Cómo se llama esta persona?" })
        Spacer(Modifier.height(8.dp))
        Text(face.hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Text("¿Cómo se llama?", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        ui.testOptions.forEach { opt ->
            val isSelected = ui.selectedAnswer == opt
            val isCorrect  = opt == face.name && ui.selectedAnswer != null
            val isWrong    = isSelected && ui.isCorrect == false
            Surface(
                onClick = { if (ui.selectedAnswer == null) onSelect(opt) },
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(vertical = 3.dp).semantics { contentDescription = "Opción: $opt" },
                shape = RoundedCornerShape(12.dp),
                color = when { isCorrect -> MaterialTheme.colorScheme.secondaryContainer; isWrong -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceVariant },
                tonalElevation = 2.dp
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(opt, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun FaceNameInstructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Caras y Nombres", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Primero verás 5 pares de cara + nombre durante 30 segundos. Memorízalos bien.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("Después deberás reconocer qué nombre corresponde a cada cara.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Row { listOf("👩‍🦰","👨‍🦳","👴").forEach { Text(it, fontSize = 40.sp) } }
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar estudio!", onStart, contentDescription = "Comenzar fase de estudio")
    }
}

@Preview(showBackground = true) @Composable private fun FaceNamePreview() { EternaMenteTheme { FaceNameInstructions {} } }
