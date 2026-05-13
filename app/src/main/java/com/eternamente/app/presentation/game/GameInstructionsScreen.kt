package com.eternamente.app.presentation.game

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen
import java.util.UUID

/**
 * Instrucciones y configuración previas al inicio de un mini-juego.
 *
 * Muestra:
 * - Descripción del juego y el dominio cognitivo que evalúa.
 * - Nivel de dificultad adaptativo actual.
 * - Ejemplo interactivo o animación de demostración (Lottie).
 *
 * Al pulsar "Iniciar", genera un UUID de sesión y navega a [GamePlayScreen].
 *
 * **Transición:** slideIn desde la derecha con [GAME_ANIM_MS] ms de duración,
 * configurada en [NavGraph].
 *
 * @param innerPadding   Padding del [Scaffold] padre.
 * @param gameId         Identificador del mini-juego a presentar.
 * @param onStartGame    Callback con el [sessionId] generado al iniciar.
 * @param onNavigateBack Volver al catálogo sin iniciar la sesión.
 */
@Composable
fun GameInstructionsScreen(
    innerPadding: PaddingValues,
    gameId: String,
    onStartGame: (sessionId: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    PlaceholderScreen(
        screenName           = "Instrucciones\n$gameId",
        accessibilityLabel   = "Instrucciones del juego $gameId, pulse Iniciar cuando esté listo",
        innerPadding         = innerPadding,
        primaryActionLabel   = "Iniciar juego",
        onPrimaryAction      = { onStartGame(UUID.randomUUID().toString()) },
        secondaryActionLabel = "Volver",
        onSecondaryAction    = onNavigateBack
    )
}
