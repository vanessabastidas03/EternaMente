package com.eternamente.app.presentation.game

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Catálogo de mini-juegos cognitivos disponibles para la sesión activa.
 *
 * El catálogo filtra y ordena los juegos según el dominio cognitivo más débil
 * del usuario (derivado de [CognitiveBaseline.weakestDomain]) y el tipo de
 * sesión actual ([SessionType]).
 *
 * Al seleccionar un juego, navega a [GameInstructionsScreen] con el [gameId].
 *
 * @param innerPadding             Padding del [Scaffold] padre.
 * @param onNavigateToInstructions Callback con el [gameId] del juego seleccionado.
 */
@Composable
fun GameCatalogScreen(
    innerPadding: PaddingValues,
    onNavigateToInstructions: (gameId: String) -> Unit
) {
    PlaceholderScreen(
        screenName         = "Catálogo de juegos",
        accessibilityLabel = "Catálogo de juegos cognitivos disponibles para tu sesión",
        innerPadding       = innerPadding,
        primaryActionLabel = "Jugar Stroop",
        onPrimaryAction    = { onNavigateToInstructions("stroop") },
        secondaryActionLabel = "Jugar Digit Span",
        onSecondaryAction    = { onNavigateToInstructions("digit_span") }
    )
}
