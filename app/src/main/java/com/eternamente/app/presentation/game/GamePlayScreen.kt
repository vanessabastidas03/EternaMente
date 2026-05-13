package com.eternamente.app.presentation.game

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Pantalla de juego activo — experiencia de pantalla completa.
 *
 * La [NavigationBar] está **oculta** durante el juego para maximizar el área
 * de interacción. Se restaura al salir hacia [GameResultScreen].
 *
 * Responsabilidades:
 * - Renderizar los estímulos y recoger las respuestas del usuario.
 * - Mantener el temporizador de la sesión.
 * - Guardar [GameResult] vía [SaveGameResultUseCase] al finalizar.
 * - Al terminar, llamar a [onGameFinished] con la puntuación normalizada.
 *
 * @param innerPadding   Padding del [Scaffold] padre.
 * @param gameId         Identificador del mini-juego en ejecución.
 * @param sessionId      UUID de la [CognitiveSession] activa.
 * @param onGameFinished Callback al terminar; recibe la puntuación normalizada (0–100).
 */
@Composable
fun GamePlayScreen(
    innerPadding: PaddingValues,
    gameId: String,
    sessionId: String,
    onGameFinished: (score: Float) -> Unit
) {
    PlaceholderScreen(
        screenName         = "Jugando\n$gameId",
        accessibilityLabel = "Juego $gameId en curso, sesión $sessionId",
        innerPadding       = innerPadding,
        primaryActionLabel = "Finalizar juego (puntaje 85)",
        onPrimaryAction    = { onGameFinished(85f) }
    )
}
