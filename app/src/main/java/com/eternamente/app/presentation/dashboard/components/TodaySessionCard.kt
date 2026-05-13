package com.eternamente.app.presentation.dashboard.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.presentation.component.EternaCard
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Tarjeta principal del Dashboard — muestra el estado de la sesión de hoy.
 *
 * Tres estados visuales:
 * 1. **Sin sesión** — llamada a la acción con botón "Comenzar sesión".
 * 2. **En progreso** — barra de progreso con los juegos completados.
 * 3. **Completada** — checkmark verde con el resumen de puntos obtenidos.
 *
 * @param todayCompleted     `true` si la sesión del día ya está completada.
 * @param sessionInProgress  `true` si hay una sesión iniciada pero no terminada.
 * @param gameResults        Juegos completados en la sesión actual.
 * @param totalGames         Total de juegos esperados en la sesión.
 * @param sessionProgress    Fracción de progreso [0.0, 1.0].
 * @param pointsEarned       Puntos obtenidos en la sesión de hoy.
 * @param onStartSession     Callback al pulsar "Comenzar sesión".
 * @param modifier           Modificador externo.
 */
@Composable
fun TodaySessionCard(
    todayCompleted: Boolean,
    sessionInProgress: Boolean,
    gameResults: Int,
    totalGames: Int,
    sessionProgress: Float,
    pointsEarned: Int,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    EternaCard(
        modifier           = modifier.fillMaxWidth(),
        contentDescription = when {
            todayCompleted    -> "Sesión de hoy completada. $pointsEarned puntos ganados."
            sessionInProgress -> "Sesión en progreso. $gameResults de $totalGames juegos completados."
            else              -> "No has hecho sesión hoy. Pulsa para comenzar."
        }
    ) {
        when {
            // ── Estado 3: completada ──────────────────────────────────────────
            todayCompleted -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.padding(4.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.secondary,
                        modifier           = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text  = "¡Sesión de hoy completada!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = "+$pointsEarned puntos ganados hoy",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // ── Estado 2: en progreso — muestra botón para continuar ──────────
            sessionInProgress -> {
                Column {
                    Text(
                        text  = "Sesión en progreso…",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress     = { sessionProgress },
                        modifier     = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .semantics { contentDescription = "$gameResults de $totalGames juegos completados" },
                        color        = MaterialTheme.colorScheme.primary,
                        trackColor   = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text  = "$gameResults de $totalGames juegos completados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    // Botón para continuar — siempre visible
                    com.eternamente.app.presentation.component.EternaFullWidthButton(
                        text               = "Continuar sesión →",
                        onClick            = onStartSession,
                        contentDescription = "Ir al catálogo de juegos para continuar"
                    )
                }
            }

            // ── Estado 1: sin sesión ──────────────────────────────────────────
            else -> {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Filled.Mood,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text  = "¡Es hora de ejercitar tu mente!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = "Completa tu sesión diaria para mantener tu racha",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    EternaFullWidthButton(
                        text               = "Comenzar sesión",
                        onClick            = onStartSession,
                        contentDescription = "Iniciar la sesión de juegos cognitivos de hoy"
                    )
                }
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Sin sesión — Claro", showBackground = true)
@Preview(name = "Sin sesión — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodaySessionCardNoSessionPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            TodaySessionCard(
                todayCompleted    = false, sessionInProgress = false,
                gameResults       = 0,     totalGames         = 3,
                sessionProgress   = 0f,    pointsEarned       = 0,
                onStartSession    = {}
            )
        }
    }
}

@Preview(name = "En progreso — Claro", showBackground = true)
@Composable
private fun TodaySessionCardInProgressPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            TodaySessionCard(
                todayCompleted    = false, sessionInProgress = true,
                gameResults       = 2,     totalGames         = 3,
                sessionProgress   = 0.67f, pointsEarned       = 0,
                onStartSession    = {}
            )
        }
    }
}

@Preview(name = "Completada — Claro", showBackground = true)
@Preview(name = "Completada — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodaySessionCardCompletedPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            TodaySessionCard(
                todayCompleted    = true, sessionInProgress = false,
                gameResults       = 3,    totalGames         = 3,
                sessionProgress   = 1f,   pointsEarned       = 15,
                onStartSession    = {}
            )
        }
    }
}
