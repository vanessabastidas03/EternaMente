package com.eternamente.app.presentation.games.digitspan

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.games.engine.GameNavigationEvent
import com.eternamente.app.presentation.games.engine.GameState
import com.eternamente.app.presentation.games.engine.InputFeedback
import com.eternamente.app.ui.theme.EternaMenteTheme

@Composable
fun DigitSpanScreen(
    innerPadding: PaddingValues,
    sessionId: String,
    userId: String,
    difficultyLevel: Int = 1,
    onGameFinished: (String, Float) -> Unit
) {
    val viewModel: DigitSpanViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val uiState   by viewModel.uiState.collectAsState()
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(sessionId) {
        viewModel.initialize(DigitSpanConfig.forDifficulty(difficultyLevel, sessionId, userId))
        viewModel.startGame()
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            if (event is GameNavigationEvent.NavigateToResult)
                onGameFinished(event.gameId, event.score)
        }
    }

    Surface(modifier = Modifier.fillMaxSize().padding(innerPadding), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = gameState,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "digit_span_state"
        ) { state ->
            when (state) {
                GameState.Idle          -> Box(Modifier.fillMaxSize())
                GameState.Instructions  -> DigitSpanInstructions(
                    config  = DigitSpanConfig.forDifficulty(difficultyLevel, "", ""),
                    onStart = { viewModel.startCountdown() }
                )
                is GameState.Countdown  -> CountdownBox(state.seconds)
                is GameState.Playing    -> DigitSpanPlayArea(
                    uiState   = uiState,
                    onDigitTap = { d ->
                        val fb = viewModel.tapDigit(d)
                        if (fb == InputFeedback.Correct)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDelete   = { viewModel.deleteLast() }
                )
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

// ── Instrucciones ─────────────────────────────────────────────────────────────

@Composable
private fun DigitSpanInstructions(config: DigitSpanConfig, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Secuencia de Números", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text(if (config.isBackward) "Repite los números en ORDEN INVERSO" else "Repite los números en el mismo orden",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        // Demo: mostrar ejemplo 3-7-2
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(3, 7, 2).forEach { d ->
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text("$d", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Text("→ escribe: ${if (config.isBackward) "2 7 3" else "3 7 2"}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Secuencia de ${config.sequenceLength} números", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        EternaFullWidthButton("¡Comenzar!", onStart, contentDescription = "Comenzar juego de secuencia")
    }
}

// ── Zona de juego ─────────────────────────────────────────────────────────────

@Composable
private fun DigitSpanPlayArea(
    uiState: DigitSpanUiState,
    onDigitTap: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Indicador de ronda y modo
        Text(
            text  = "Ronda ${uiState.roundIndex + 1} de ${DigitSpanEngine.TOTAL_ROUNDS}${if (uiState.isBackward) " — INVERSO" else ""}",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        // Dígito mostrado o indicador de fase
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape).background(
                when (uiState.phase) {
                    SpanPhase.SHOWING  -> if (uiState.displayedDigit != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    SpanPhase.INPUT    -> MaterialTheme.colorScheme.secondaryContainer
                    SpanPhase.FEEDBACK -> if (uiState.lastWasCorrect == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            when (uiState.phase) {
                SpanPhase.SHOWING  -> Text(
                    text  = uiState.displayedDigit?.toString() ?: "",
                    fontSize = 56.sp, fontWeight = FontWeight.Bold,
                    color = if (uiState.displayedDigit != null) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "Dígito: ${uiState.displayedDigit ?: "esperando"}" }
                )
                SpanPhase.INPUT    -> Text("?", fontSize = 48.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                SpanPhase.FEEDBACK -> Text(
                    text = if (uiState.lastWasCorrect == true) "✓" else "✗",
                    fontSize = 48.sp, color = MaterialTheme.colorScheme.onError
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Entrada del usuario (puntos/dígitos)
        if (uiState.phase == SpanPhase.INPUT) {
            Text(
                text  = uiState.userInput.joinToString(" – ").ifEmpty { "—" },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = "Tu respuesta: ${uiState.userInput.joinToString(" ")}" }
            )
            Spacer(Modifier.height(8.dp))
            Text("${uiState.userInput.size} / ${uiState.sequenceLength}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.weight(1f))

        // Numpad personalizado 3×4 (1-9, [del], 0, [_])
        if (uiState.phase == SpanPhase.INPUT) {
            NumpadGrid(onDigitTap = onDigitTap, onDelete = onDelete)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NumpadGrid(onDigitTap: (Int) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-2, 0, -1))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    when (key) {
                        -2 -> Spacer(Modifier.weight(1f))
                        -1 -> FilledIconButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f).height(64.dp).semantics { contentDescription = "Borrar último dígito" },
                            shape = RoundedCornerShape(12.dp)
                        ) { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null) }
                        else -> NumpadKey(digit = key, onClick = { onDigitTap(key) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun NumpadKey(digit: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().semantics { contentDescription = "Tecla $digit" }) {
            Text("$digit", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CountdownBox(seconds: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$seconds", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
            Text("¡Prepárate!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.85f))
        }
    }
}

@Preview(name = "Numpad — Claro", showBackground = true)
@Preview(name = "Numpad — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NumpadPreview() {
    EternaMenteTheme { Surface(Modifier.padding(16.dp)) { NumpadGrid({}, {}) } }
}
