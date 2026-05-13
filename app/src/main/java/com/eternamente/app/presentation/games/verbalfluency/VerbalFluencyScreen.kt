package com.eternamente.app.presentation.games.verbalfluency

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
class VerbalFluencyViewModel @Inject constructor(
    save: SaveGameResultUseCase, update: UpdateGamificationUseCase,
    session: SessionRepository, prefs: UserPreferencesRepository
) : GameBaseViewModel<VerbalFluencyConfig, VerbalFluencyResult>(save, update, session, prefs) {
    private var _engine: VerbalFluencyEngine? = null
    override val engine: GameEngine<VerbalFluencyConfig, VerbalFluencyResult> get() = requireNotNull(_engine)
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(VerbalFluencyUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<VerbalFluencyUiState> = _uiState.asStateFlow()

    fun initialize(c: VerbalFluencyConfig) {
        if (_engine != null) return
        val e = VerbalFluencyEngine(c)
        _engine = e
        viewModelScope.launch { e.uiState.collect { _uiState.value = it } }
    }
    fun startCountdown() = _engine?.startCountdown()
    fun submitWord(word: String): InputFeedback = onUserInput(UserInput.TapTarget(word))

    override fun buildDomainResult(r: VerbalFluencyResult) = com.eternamente.app.domain.model.GameResult(
        id = java.util.UUID.randomUUID().toString(), sessionId = r.sessionId, gameId = r.gameId,
        domain = com.eternamente.app.domain.model.CognitiveDomain.LANGUAGE,
        scoreRaw = r.wordsPerMinute,
        scoreNormalized = (r.wordsPerMinute / 20f * 100f).coerceIn(0f, 100f),
        reactionTimeMsAvg = r.metrics.mean, reactionTimeMsP50 = r.metrics.median,
        accuracyPct = r.metrics.accuracyPct, errorsCount = r.metrics.errorCount + r.repetitions,
        difficultyLevel = r.difficultyReached
    )
}

@Composable
fun VerbalFluencyScreen(
    innerPadding: PaddingValues, sessionId: String, userId: String,
    difficultyLevel: Int = 1, onGameFinished: (String, Float) -> Unit
) {
    val viewModel: VerbalFluencyViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState(VerbalFluencyUiState())
    val haptic    = LocalHapticFeedback.current
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(sessionId) {
        viewModel.initialize(VerbalFluencyConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { e ->
            if (e is GameNavigationEvent.NavigateToResult) onGameFinished(e.gameId, e.score)
        }
    }

    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
        when (val state = gameState) {
            GameState.Instructions -> VFInstructions(uiState.category) { viewModel.startCountdown() }
            is GameState.Countdown -> VFCountdown(state.seconds)
            is GameState.Playing   -> VFPlayArea(
                uiState = uiState,
                inputText = inputText,
                onInputChange = { inputText = it },
                onSubmit = {
                    val fb = viewModel.submitWord(inputText.trim())
                    if (fb == InputFeedback.Correct) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        inputText = ""
                    } else if (fb == InputFeedback.Incorrect) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        inputText = ""
                    }
                }
            )
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun VFPlayArea(uiState: VerbalFluencyUiState, inputText: String, onInputChange: (String) -> Unit, onSubmit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(uiState.category.displayName, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                Text("${uiState.enteredWords.size} palabras", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(64.dp), Alignment.Center) {
                CircularProgressIndicator(progress = { uiState.timeLeft / 60f }, modifier = Modifier.fillMaxSize(), color = if (uiState.timeLeft > 20) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, strokeWidth = 5.dp)
                Text("${uiState.timeLeft}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (uiState.timeLeft > 20) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = inputText, onValueChange = onInputChange,
            label = { Text("Escribe una ${uiState.category.displayName.lowercase().removeSuffix("s")}…") },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Campo para escribir palabras de ${uiState.category.displayName}" },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            trailingIcon = {
                Button(onClick = onSubmit, modifier = Modifier.padding(end = 4.dp)) { Text("OK") }
            },
            isError = uiState.lastWordValid == false,
            supportingText = when (uiState.lastWordValid) {
                false -> { { Text("Palabra no válida o repetida") } }
                true  -> { { Text("✓ ¡Correcta!") } }
                null  -> null
            }
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(uiState.enteredWords.reversed()) { word ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                    Text(word.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                    Text("✓", color = MaterialTheme.colorScheme.secondary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun VFInstructions(category: FluencyCategory, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Palabras en Categoría", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Escribe tantos ${category.displayName.lowercase()} como puedas en 60 segundos.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("Categoría: ${category.displayName}", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de fluencia verbal")
    }
}

@Composable
private fun VFCountdown(s: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$s", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            Text("¡Prepárate!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.85f))
        }
    }
}

@Preview(showBackground = true) @Composable private fun VFPreview() { EternaMenteTheme { VFInstructions(FluencyCategory.ANIMALS) {} } }
