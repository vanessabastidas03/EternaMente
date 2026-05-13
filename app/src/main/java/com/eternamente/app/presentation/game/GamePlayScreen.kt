package com.eternamente.app.presentation.game

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen
import com.eternamente.app.presentation.games.corsiblock.CorsiScreen
import com.eternamente.app.presentation.games.digitspan.DigitSpanScreen
import com.eternamente.app.presentation.games.flashcolor.FlashColorScreen
import com.eternamente.app.presentation.games.memorymatch.MemoryMatchConfig
import com.eternamente.app.presentation.games.memorymatch.MemoryMatchScreen
import com.eternamente.app.presentation.games.namingimage.NamingImageScreen
import com.eternamente.app.presentation.games.spotdiff.SpotDiffScreen
import com.eternamente.app.presentation.games.stroop.StroopScreen
import com.eternamente.app.presentation.games.temporalorientation.TemporalOrientationScreen
import com.eternamente.app.presentation.games.trailmaking.TrailMakingScreen
import com.eternamente.app.presentation.games.verbalfluency.VerbalFluencyScreen

/**
 * Router central de juegos cognitivos de EternaMente.
 *
 * Despacha al composable del juego correcto según [gameId].
 * Cada juego implementa [GameEngine] + ViewModel + Screen siguiendo
 * el patrón definido en `presentation/games/engine/`.
 *
 * ## Catálogo de juegos implementados
 * | ID | Juego | Dominio cognitivo |
 * |----|-------|-------------------|
 * | `memory_match`          | Memorama de Pares       | Memoria visual episódica |
 * | `digit_span`            | Secuencia de Números    | Memoria de trabajo |
 * | `flash_color`           | Flash de Colores        | Atención sostenida |
 * | `trail_making`          | Conecta Puntos          | Funciones ejecutivas |
 * | `naming_image`          | Nombra la Imagen        | Lenguaje |
 * | `verbal_fluency`        | Palabras en Categoría   | Fluidez verbal |
 * | `spot_diff`             | Diferencias             | Atención dividida |
 * | `stroop`                | Stroop de Colores       | Control inhibitorio |
 * | `corsi_block`           | Reproduce el Patrón     | Memoria visoespacial |
 * | `temporal_orientation`  | Orientación Temporal    | Orientación |
 *
 * @param innerPadding   Padding del [Scaffold] del NavGraph.
 * @param gameId         Identificador estable del juego.
 * @param sessionId      UUID de la sesión cognitiva activa.
 * @param userId         UUID del usuario.
 * @param difficultyLevel Nivel 1–5 determinado por [DifficultyManager].
 * @param onGameFinished Callback con `(gameId, scoreNormalized)` al completar.
 */
@Composable
fun GamePlayScreen(
    innerPadding: PaddingValues,
    gameId: String,
    sessionId: String,
    userId: String        = "",
    difficultyLevel: Int  = 1,
    onGameFinished: (score: Float) -> Unit
) {
    val onFinished: (String, Float) -> Unit = { _, score -> onGameFinished(score) }

    when (gameId) {
        MemoryMatchConfig.GAME_ID -> MemoryMatchScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        "digit_span"              -> DigitSpanScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        "flash_color"             -> FlashColorScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        "trail_making"            -> TrailMakingScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        "naming_image"            -> NamingImageScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        "verbal_fluency"          -> VerbalFluencyScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        "spot_diff"               -> SpotDiffScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        "stroop"                  -> StroopScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        "corsi_block"             -> CorsiScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        "temporal_orientation"    -> TemporalOrientationScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
        else                      -> PlaceholderScreen(
            screenName         = "Jugando\n$gameId",
            accessibilityLabel = "Juego $gameId en curso",
            innerPadding       = innerPadding,
            primaryActionLabel = "Finalizar (85 pts)",
            onPrimaryAction    = { onGameFinished(85f) }
        )
    }
}
