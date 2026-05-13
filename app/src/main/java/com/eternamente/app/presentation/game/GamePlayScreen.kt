package com.eternamente.app.presentation.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.presentation.common.PlaceholderScreen
import com.eternamente.app.presentation.games.clockdrawing.ClockDrawingScreen
import com.eternamente.app.presentation.games.corsiblock.CorsiScreen
import com.eternamente.app.presentation.games.digitspan.DigitSpanScreen
import com.eternamente.app.presentation.games.facename.FaceNameScreen
import com.eternamente.app.presentation.games.flashcolor.FlashColorScreen
import com.eternamente.app.presentation.games.memorymatch.MemoryMatchConfig
import com.eternamente.app.presentation.games.memorymatch.MemoryMatchScreen
import com.eternamente.app.presentation.games.mentalcalc.MentalCalcScreen
import com.eternamente.app.presentation.games.namingimage.NamingImageScreen
import com.eternamente.app.presentation.games.prospectivememory.ProspectiveMemScreen
import com.eternamente.app.presentation.games.readingcomprehension.ReadingCompScreen
import com.eternamente.app.presentation.games.spotdiff.SpotDiffScreen
import com.eternamente.app.presentation.games.stroop.StroopScreen
import com.eternamente.app.presentation.games.temporalorientation.TemporalOrientationScreen
import com.eternamente.app.presentation.games.trailmaking.TrailMakingScreen
import com.eternamente.app.presentation.games.verbalfluency.VerbalFluencyScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ── ViewModel host ────────────────────────────────────────────────────────────

@HiltViewModel
class GamePlayHostViewModel @Inject constructor(
    userPrefsRepository: UserPreferencesRepository
) : ViewModel() {
    val userId: StateFlow<String> = userPrefsRepository.preferences
        .map { it.currentUserId ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
}

// ── Router central ────────────────────────────────────────────────────────────

@Composable
fun GamePlayScreen(
    innerPadding: PaddingValues,
    gameId: String,
    sessionId: String,
    difficultyLevel: Int  = 1,
    onGameFinished: (score: Float) -> Unit
) {
    val hostVm: GamePlayHostViewModel = hiltViewModel()
    val userId by hostVm.userId.collectAsState()
    val onFinished: (String, Float) -> Unit = { _, score -> onGameFinished(score) }

    var showExitDialog by remember { mutableStateOf(false) }

    // Intercepta el botón físico Atrás para mostrar confirmación
    BackHandler { showExitDialog = true }

    Box(Modifier.fillMaxSize()) {
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
            "clock_drawing"           -> ClockDrawingScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
            "face_name"               -> FaceNameScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
            "mental_calc"             -> MentalCalcScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
            "prospective_memory"      -> ProspectiveMemScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
            "reading_comprehension"   -> ReadingCompScreen(innerPadding, sessionId, userId, difficultyLevel, onFinished)
            else -> PlaceholderScreen(
                screenName         = "Jugando\n$gameId",
                accessibilityLabel = "Juego $gameId en curso",
                innerPadding       = innerPadding,
                primaryActionLabel = "Finalizar (85 pts)",
                onPrimaryAction    = { onGameFinished(85f) }
            )
        }

        // Botón flotante "Salir" — visible en todo momento durante el juego
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(innerPadding)
                .padding(end = 12.dp, top = 8.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            IconButton(onClick = { showExitDialog = true }) {
                Icon(
                    imageVector        = Icons.Filled.Close,
                    contentDescription = "Salir del juego",
                    tint               = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    // Diálogo de confirmación de salida
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title   = { Text("¿Salir del juego?") },
            text    = { Text("Se perderá el progreso de esta partida. ¿Estás seguro/a?") },
            confirmButton = {
                Button(
                    onClick = { showExitDialog = false; onGameFinished(0f) },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Continuar jugando") }
            }
        )
    }
}
