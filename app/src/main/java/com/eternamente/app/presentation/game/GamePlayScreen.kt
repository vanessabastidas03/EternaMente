package com.eternamente.app.presentation.game

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen
import com.eternamente.app.presentation.games.memorymatch.MemoryMatchConfig
import com.eternamente.app.presentation.games.memorymatch.MemoryMatchScreen

/**
 * Router de pantalla de juego activo.
 *
 * Despacha al motor de juego correcto según [gameId].
 * Cada juego implementa su propio composable siguiendo el patrón
 * `GameEngine<Config, Result>` definido en `presentation/games/engine/`.
 *
 * ## Juegos implementados
 * - `"memory_match"` → [MemoryMatchScreen] (Memorama de Pares — memoria visual episódica)
 *
 * ## Agregar un nuevo juego
 * 1. Crear `presentation/games/<nombre>/` con Engine, ViewModel y Screen.
 * 2. Añadir una rama `"<game_id>"` en el `when` de esta función.
 *
 * @param innerPadding   Padding del [Scaffold] del NavGraph.
 * @param gameId         Identificador estable del juego (ej. `"memory_match"`).
 * @param sessionId      UUID de la [com.eternamente.app.domain.model.CognitiveSession] activa.
 * @param userId         UUID del usuario que juega (obtenido de DataStore en NavGraph).
 * @param difficultyLevel Nivel de dificultad (1–5).
 * @param onGameFinished Callback con la puntuación normalizada [0–100] al terminar.
 */
@Composable
fun GamePlayScreen(
    innerPadding: PaddingValues,
    gameId: String,
    sessionId: String,
    userId: String           = "",
    difficultyLevel: Int     = 1,
    onGameFinished: (score: Float) -> Unit
) {
    when (gameId) {
        MemoryMatchConfig.GAME_ID ->
            MemoryMatchScreen(
                innerPadding    = innerPadding,
                sessionId       = sessionId,
                userId          = userId,
                difficultyLevel = difficultyLevel,
                onGameFinished  = { _, score -> onGameFinished(score) }
            )

        else ->
            // Placeholder para juegos aún no implementados
            PlaceholderScreen(
                screenName         = "Jugando\n$gameId",
                accessibilityLabel = "Juego $gameId en curso, sesión $sessionId",
                innerPadding       = innerPadding,
                primaryActionLabel = "Finalizar juego (puntaje 85)",
                onPrimaryAction    = { onGameFinished(85f) }
            )
    }
}
