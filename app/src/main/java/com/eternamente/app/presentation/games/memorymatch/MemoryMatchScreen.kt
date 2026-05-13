package com.eternamente.app.presentation.games.memorymatch

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.presentation.games.engine.GameNavigationEvent
import com.eternamente.app.presentation.games.engine.GameState
import com.eternamente.app.presentation.games.engine.InputFeedback
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Pantalla completa del juego Memorama de Pares.
 *
 * Gestiona todos los estados del juego en una sola composable raíz:
 * - [GameState.Instructions] → pantalla de instrucciones con demo interactivo
 * - [GameState.Countdown] → cuenta regresiva 3-2-1
 * - [GameState.Playing] → grid de tarjetas + timer + puntaje
 * - [GameState.Paused] → no se muestra (no hay pausa en este juego)
 * - [GameState.Completed] → gestionado por el evento de navegación
 *
 * ## Accesibilidad
 * - Tarjetas mínimo 64 dp × 64 dp (ajustadas al ancho de pantalla).
 * - Feedback háptico: corto al encontrar par, largo al agotar tiempo.
 * - contentDescription en cada tarjeta describe su estado para TalkBack.
 * - Sin sonido activado por defecto.
 *
 * @param innerPadding         Padding del [Scaffold] del NavGraph.
 * @param sessionId            UUID de la sesión cognitiva activa.
 * @param userId               UUID del usuario que juega.
 * @param difficultyLevel      Nivel de dificultad (1–5).
 * @param onGameFinished       Callback con `(gameId, scoreNormalized)` al completar.
 * @param onNavigateBack       Volver al catálogo sin terminar el juego.
 */
@Composable
fun MemoryMatchScreen(
    innerPadding: PaddingValues,
    sessionId: String,
    userId: String,
    difficultyLevel: Int         = 1,
    onGameFinished: (String, Float) -> Unit,
    onNavigateBack: () -> Unit   = {}
) {
    val viewModel: MemoryMatchViewModel = hiltViewModel()
    val gameState by viewModel.gameState.collectAsState()
    val cards     by viewModel.cards.collectAsState()
    val score     by viewModel.score.collectAsState()
    val config    by viewModel.config.collectAsState()
    val haptic    = LocalHapticFeedback.current

    // ── Inicialización única ──────────────────────────────────────────────────
    LaunchedEffect(sessionId) {
        val cfg = MemoryMatchConfig.forDifficulty(difficultyLevel, sessionId, userId)
        viewModel.initialize(cfg)
        viewModel.startGame()
    }

    // ── Evento de navegación ──────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is GameNavigationEvent.NavigateToResult ->
                    onGameFinished(event.gameId, event.score)
            }
        }
    }

    // ── Renderizado según estado ──────────────────────────────────────────────
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState  = gameState,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label        = "memory_match_state"
        ) { state ->
            when (state) {
                GameState.Idle -> MemoryMatchLoading()

                GameState.Instructions ->
                    MemoryMatchInstructions(
                        config      = config,
                        onStart     = { viewModel.startCountdown() },
                        onBack      = onNavigateBack
                    )

                is GameState.Countdown ->
                    MemoryMatchCountdown(seconds = state.seconds)

                is GameState.Playing ->
                    MemoryMatchPlayArea(
                        cards       = cards,
                        score       = score,
                        config      = config,
                        timeLeft    = state.timeLeft,
                        onCardTap   = { idx ->
                            val feedback = viewModel.onCardTapped(idx)
                            if (feedback == InputFeedback.Correct) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )

                GameState.Paused, is GameState.Completed ->
                    Box(Modifier.fillMaxSize()) // El navigation event maneja Completed

                else -> MemoryMatchLoading()
            }
        }
    }

    // Vibración larga cuando el tiempo se agota (estado Completed por timeout)
    LaunchedEffect(gameState) {
        val result = (gameState as? GameState.Completed)?.result as? MemoryMatchResult
        if (result?.completedByTimeout == true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Pantalla de instrucciones
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MemoryMatchInstructions(
    config: MemoryMatchConfig?,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text  = "Memorama de Pares",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text  = "Encuentra todos los pares de tarjetas iguales",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // ── Demo interactivo con 4 tarjetas ───────────────────────────────────
        Text(
            text  = "Ejemplo:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        DemoGrid()
        Spacer(Modifier.height(16.dp))

        // ── Reglas ────────────────────────────────────────────────────────────
        InstructionRule(emoji = "👆", text = "Toca dos tarjetas para voltearlas")
        InstructionRule(emoji = "✅", text = "Si son iguales: ¡par encontrado! +10 pts")
        InstructionRule(emoji = "❌", text = "Si son distintas: se vuelven a tapar  -2 pts")
        if (config != null) {
            InstructionRule(
                emoji = "⏱️",
                text  = "Tienes ${config.timeLimitSeconds} segundos para encontrar ${config.totalPairs} pares"
            )
        }

        Spacer(Modifier.weight(1f))

        EternaFullWidthButton(
            text               = "¡Comenzar!",
            onClick            = onStart,
            contentDescription = "Comenzar el juego de memoria"
        )

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DemoGrid() {
    // 2x2 demo con un par revelado y uno oculto
    val demoCards = listOf(
        CardState(0, "🐶", isFaceUp = true,  isMatched = true),
        CardState(1, "🐱", isFaceUp = false),
        CardState(2, "🐶", isFaceUp = true,  isMatched = true),
        CardState(3, "🐱", isFaceUp = false)
    )
    LazyVerticalGrid(
        columns          = GridCells.Fixed(2),
        modifier         = Modifier.size(140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        itemsIndexed(demoCards) { _, card ->
            CardItem(card = card, cardSize = 64.dp, onClick = {})
        }
    }
}

@Composable
private fun InstructionRule(emoji: String, text: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 22.sp, modifier = Modifier.semantics { contentDescription = "" })
        Text(
            text     = "  $text",
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onBackground
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Pantalla de juego activo
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MemoryMatchPlayArea(
    cards: List<CardState>,
    score: Int,
    config: MemoryMatchConfig?,
    timeLeft: Int?,
    onCardTap: (Int) -> Unit
) {
    val screenWidth   = LocalConfiguration.current.screenWidthDp.dp
    val cols          = config?.columns ?: 4
    val horizontalPad = 16.dp
    val gap           = 6.dp
    // Calcular tamaño de tarjeta garantizando mínimo 48 dp
    val cardSize: Dp  = ((screenWidth - horizontalPad * 2 - gap * (cols - 1)) / cols)
        .coerceAtLeast(48.dp)
        .coerceAtMost(80.dp)

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Barra de estado ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Puntaje
            Column {
                Text(
                    text  = "Puntos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text  = "$score",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = "Puntuación actual: $score" }
                )
            }

            // Pares encontrados
            val matched = cards.count { it.isMatched } / 2
            val total   = config?.totalPairs ?: 8
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text  = "Pares",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text  = "$matched / $total",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { contentDescription = "$matched de $total pares encontrados" }
                )
            }

            // Timer circular
            CircularTimer(
                totalSeconds     = config?.timeLimitSeconds ?: 90,
                remainingSeconds = timeLeft,
                modifier         = Modifier.size(64.dp)
            )
        }

        // ── Grid de tarjetas ──────────────────────────────────────────────────
        LazyVerticalGrid(
            columns               = GridCells.Fixed(cols),
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPad),
            contentPadding        = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement   = Arrangement.spacedBy(gap),
            userScrollEnabled     = false
        ) {
            itemsIndexed(
                items = cards,
                key   = { _, card -> card.id }
            ) { index, card ->
                CardItem(
                    card     = card,
                    cardSize = cardSize,
                    onClick  = { if (card.isInteractable) onCardTap(index) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CardItem — animación de volteo (flip) a 60 fps
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Tarjeta del Memorama con animación de volteo 3D usando `graphicsLayer`.
 *
 * ## Técnica de animación
 * - `animateFloatAsState` con `tween(400, FastOutSlowInEasing)` → 60 fps garantizados.
 * - El `cameraDistance = 8f * density` aporta perspectiva realista.
 * - A 90° de rotación el card es una línea → el cambio cara/dorso es imperceptible.
 * - El contenido frontal se contra-rota `rotationY = rotation - 180f` para aparecer legible.
 *
 * ## Accesibilidad
 * - Mínimo 64 dp de altura (ajustable por el llamador).
 * - contentDescription describe el estado de la tarjeta para TalkBack.
 * - No hay texto visible en la tarjeta — solo el símbolo visual.
 */
@Composable
fun CardItem(
    card: CardState,
    cardSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetRotation = if (card.isFaceUp || card.isMatched) 180f else 0f

    val rotation by animateFloatAsState(
        targetValue   = targetRotation,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label         = "card_flip_${card.id}"
    )

    val accessDesc = when {
        card.isMatched  -> "Tarjeta emparejada: ${card.symbol}"
        card.isFaceUp   -> "Tarjeta volteada: ${card.symbol}"
        else            -> "Tarjeta tapada"
    }

    Box(
        modifier         = modifier
            .size(cardSize)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = card.isInteractable, onClick = onClick)
            .semantics { contentDescription = accessDesc },
        contentAlignment = Alignment.Center
    ) {
        // ── Dorso (visible cuando rotation in [0, 90°]) ───────────────────────
        Surface(
            modifier  = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY      = rotation
                    cameraDistance = 8f * density
                    // Ocultar cuando está del revés para evitar artefactos
                    if (rotation > 90f) alpha = 0f
                },
            shape     = RoundedCornerShape(10.dp),
            color     = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text     = "?",
                    fontSize = (cardSize.value * 0.35f).sp,
                    color    = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Cara (visible cuando rotation in [90°, 180°]) ────────────────────
        Surface(
            modifier  = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Contra-rotar para que el contenido aparezca legible
                    rotationY      = rotation - 180f
                    cameraDistance = 8f * density
                    if (rotation <= 90f) alpha = 0f
                },
            shape     = RoundedCornerShape(10.dp),
            color     = when {
                card.isMatched -> MaterialTheme.colorScheme.secondaryContainer
                else           -> MaterialTheme.colorScheme.surface
            },
            border    = if (card.isMatched) {
                androidx.compose.foundation.BorderStroke(
                    2.dp, MaterialTheme.colorScheme.secondary
                )
            } else null,
            tonalElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text     = card.symbol,
                    fontSize = (cardSize.value * 0.45f).sp,  // Solo símbolo visual, sin texto
                    modifier = Modifier.semantics { contentDescription = "" }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Timer circular
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CircularTimer(
    totalSeconds: Int,
    remainingSeconds: Int?,
    modifier: Modifier = Modifier
) {
    val remaining = remainingSeconds ?: totalSeconds
    val progress  = if (totalSeconds > 0) remaining.toFloat() / totalSeconds else 1f

    val timerColor = when {
        progress > 0.5f  -> MaterialTheme.colorScheme.primary
        progress > 0.25f -> MaterialTheme.colorScheme.tertiary
        else             -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier         = modifier.semantics {
            contentDescription = "Tiempo restante: $remaining segundos"
        },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress    = { progress },
            modifier    = Modifier.fillMaxSize(),
            color       = timerColor,
            trackColor  = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 5.dp
        )
        Text(
            text  = "$remaining",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = timerColor
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Pantallas auxiliares
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MemoryMatchCountdown(seconds: Int) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "$seconds",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.semantics { contentDescription = "El juego comienza en $seconds" }
            )
            Text(
                text  = "¡Prepárate!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun MemoryMatchLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "CardItem — Tapada — Claro", showBackground = true)
@Preview(name = "CardItem — Tapada — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CardItemHiddenPreview() {
    EternaMenteTheme {
        Surface(modifier = androidx.compose.ui.Modifier.padding(8.dp)) {
            CardItem(
                card     = CardState(id = 0, symbol = "🐶"),
                cardSize = 72.dp,
                onClick  = {}
            )
        }
    }
}

@Preview(name = "CardItem — Revelada — Claro", showBackground = true)
@Composable
private fun CardItemRevealedPreview() {
    EternaMenteTheme {
        Surface(modifier = androidx.compose.ui.Modifier.padding(8.dp)) {
            CardItem(
                card     = CardState(id = 0, symbol = "🐶", isFaceUp = true),
                cardSize = 72.dp,
                onClick  = {}
            )
        }
    }
}

@Preview(name = "CardItem — Emparejada — Claro", showBackground = true)
@Composable
private fun CardItemMatchedPreview() {
    EternaMenteTheme {
        Surface(modifier = androidx.compose.ui.Modifier.padding(8.dp)) {
            CardItem(
                card     = CardState(id = 0, symbol = "🐶", isFaceUp = true, isMatched = true),
                cardSize = 72.dp,
                onClick  = {}
            )
        }
    }
}

@Preview(name = "CircularTimer — Lleno — Claro", showBackground = true)
@Preview(name = "CircularTimer — Urgente — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CircularTimerPreview() {
    EternaMenteTheme {
        Surface(modifier = androidx.compose.ui.Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularTimer(totalSeconds = 90, remainingSeconds = 75, modifier = androidx.compose.ui.Modifier.size(64.dp))
                CircularTimer(totalSeconds = 90, remainingSeconds = 30, modifier = androidx.compose.ui.Modifier.size(64.dp))
                CircularTimer(totalSeconds = 90, remainingSeconds = 10, modifier = androidx.compose.ui.Modifier.size(64.dp))
            }
        }
    }
}

@Preview(name = "Instrucciones — Claro", showBackground = true)
@Preview(name = "Instrucciones — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MemoryMatchInstructionsPreview() {
    EternaMenteTheme {
        Surface {
            MemoryMatchInstructions(
                config  = MemoryMatchConfig.forDifficulty(1, "session-1", "user-1"),
                onStart = {},
                onBack  = {}
            )
        }
    }
}

@Preview(name = "Grid 4x4 — Claro", showBackground = true, widthDp = 400)
@Composable
private fun MemoryMatchPlayAreaPreview() {
    EternaMenteTheme {
        Surface {
            MemoryMatchPlayArea(
                cards     = List(16) { i ->
                    CardState(
                        id        = i,
                        symbol    = CardSet.ANIMALS.symbols[i % 8],
                        isFaceUp  = i in listOf(0, 8),
                        isMatched = i in listOf(2, 10)
                    )
                },
                score     = 20,
                config    = MemoryMatchConfig.forDifficulty(1, "session-1", "user-1"),
                timeLeft  = 67,
                onCardTap = {}
            )
        }
    }
}
