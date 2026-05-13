package com.eternamente.app.presentation.games.namingimage

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
class NamingImageViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<NamingImageConfig, NamingImageResult>(save, update, session, prefs) {
    private var _engine: NamingImageEngine? = null
    override val engine: GameEngine<NamingImageConfig, NamingImageResult> get() = requireNotNull(_engine)
    val uiState get() = _engine?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(NamingImageUiState())

    fun initialize(c: NamingImageConfig) { if (_engine != null) return; _engine = NamingImageEngine(c) }
    fun startCountdown() = _engine?.startCountdown()
    fun selectOption(idx: Int): InputFeedback = onUserInput(UserInput.SelectOption(idx))

    override fun buildDomainResult(r: NamingImageResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.LANGUAGE,
        scoreRaw = r.correctAnswers.toFloat(),
        scoreNormalized = (r.correctAnswers.toFloat() / r.totalImages * 100f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun NamingImageScreen(
    innerPadding: PaddingValues, sessionId: String, userId: String,
    difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit
) {
    val viewModel: NamingImageViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(NamingImageUiState())
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(NamingImageConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { e ->
            if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score)
        }
    }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        AnimatedContent(gameState, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "ni") { state ->
            when (state) {
                GameState.Instructions -> NamingInstructions { viewModel.startCountdown() }
                is GameState.Countdown -> NamingCountdown(state.seconds)
                is GameState.Playing   -> NamingPlayArea(uiState) { idx ->
                    val fb = viewModel.selectOption(idx)
                    if (fb == InputFeedback.Correct) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun NamingPlayArea(ui: NamingImageUiState, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Progreso + timer
        LinearProgressIndicator(
            progress = { ui.timeRemainingMs.toFloat() / 8_000f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color    = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text("${ui.imageIndex + 1} / ${ui.totalImages}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        // Imagen (emoji grande)
        Text(ui.emoji, fontSize = 100.sp, modifier = Modifier.semantics { contentDescription = "Imagen: ¿cómo se llama esto?" })
        Spacer(Modifier.height(32.dp))
        Text("¿Cómo se llama?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        // 4 opciones en 2×2
        val optionColors = listOf(
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.surfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ui.options.chunked(2).forEachIndexed { rowIdx, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { colIdx, opt ->
                        val idx = rowIdx * 2 + colIdx
                        val isSelected = ui.selectedIndex == idx
                        val isCorrect  = ui.correctName == opt
                        val bgColor    = when {
                            isSelected && isCorrect  == true  -> MaterialTheme.colorScheme.secondary
                            isSelected && isCorrect  == false -> MaterialTheme.colorScheme.error
                            else -> optionColors[idx % optionColors.size]
                        }
                        Surface(
                            onClick = { if (ui.selectedIndex == null) onSelect(idx) },
                            modifier = Modifier.weight(1f).height(64.dp).semantics { contentDescription = "Opción: $opt" },
                            shape = RoundedCornerShape(12.dp), color = bgColor, tonalElevation = 2.dp
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(opt, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NamingInstructions(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Nombra la Imagen", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Verás una imagen y 4 palabras. Pulsa la palabra correcta lo más rápido posible.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("Tienes 8 segundos por imagen.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text("🐶", fontSize = 80.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Perro","Gato","Conejo","Mesa").forEachIndexed { i, w ->
                Surface(shape = RoundedCornerShape(8.dp), color = if (i==0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(width=72.dp, height=44.dp)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text(w, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de nombrar imágenes")
    }
}

@Composable
private fun NamingCountdown(s: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$s", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            Text("¡Prepárate!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.85f))
        }
    }
}

@Preview(showBackground = true) @Composable private fun NamingPreview() { EternaMenteTheme { NamingInstructions {} } }
