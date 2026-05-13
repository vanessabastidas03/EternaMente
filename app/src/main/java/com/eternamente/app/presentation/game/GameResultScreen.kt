package com.eternamente.app.presentation.game

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Pantalla de resultado al finalizar un mini-juego.
 *
 * Muestra:
 * - Puntuación normalizada del juego ([score] / 100).
 * - Comparativa con la partida anterior y el baseline del usuario.
 * - Puntos y medallas ganados ([GamificationProfile] actualizado).
 * - Opciones para jugar de nuevo o volver al panel principal.
 *
 * **Back stack:** esta pantalla reemplaza a [GamePlayScreen] (`popUpTo GamePlay inclusive=true`)
 * para que el botón atrás lleve directamente al catálogo.
 *
 * @param innerPadding           Padding del [Scaffold] padre.
 * @param gameId                 Identificador del juego completado.
 * @param score                  Puntuación normalizada (0–100).
 * @param onNavigateToDashboard  Volver al panel principal.
 * @param onPlayAgain            Volver al catálogo para elegir otro juego.
 */
@Composable
fun GameResultScreen(
    innerPadding: PaddingValues,
    gameId: String,
    score: Float,
    onNavigateToDashboard: () -> Unit,
    onPlayAgain: () -> Unit
) {
    PlaceholderScreen(
        screenName           = "Resultado\n$gameId\n${"%.1f".format(score)} pts",
        accessibilityLabel   = "Resultado del juego $gameId: ${"%.1f".format(score)} puntos de 100",
        innerPadding         = innerPadding,
        primaryActionLabel   = "Volver al inicio",
        onPrimaryAction      = onNavigateToDashboard,
        secondaryActionLabel = "Jugar otro juego",
        onSecondaryAction    = onPlayAgain
    )
}
