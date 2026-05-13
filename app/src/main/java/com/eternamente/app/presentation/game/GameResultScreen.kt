package com.eternamente.app.presentation.game

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eternamente.app.presentation.component.EternaButton
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.component.EternaOutlinedButton
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Pantalla de resultado al finalizar un mini-juego.
 *
 * Muestra la puntuación normalizada (0–100), nivel de desempeño,
 * mensaje motivacional y opciones de navegación.
 */
@Composable
fun GameResultScreen(
    innerPadding: PaddingValues,
    gameId: String,
    score: Float,
    onNavigateToDashboard: () -> Unit,
    onPlayAgain: () -> Unit
) {
    val gameName = remember(gameId) { gameNameFor(gameId) }
    val scoreLevel = score
    val emoji   = when { scoreLevel >= 85f -> "🏆"; scoreLevel >= 70f -> "⭐"; scoreLevel >= 50f -> "👍"; scoreLevel >= 30f -> "💪"; else -> "🎯" }
    val level   = when { scoreLevel >= 85f -> "¡Excelente!"; scoreLevel >= 70f -> "Muy bien"; scoreLevel >= 50f -> "Bien"; scoreLevel >= 30f -> "Sigue practicando"; else -> "Sigue intentando" }
    val message = when { scoreLevel >= 85f -> "Tu memoria cognitiva está en un nivel muy alto. ¡Sigue así!"; scoreLevel >= 70f -> "Buen rendimiento. Con práctica diaria seguirás mejorando."; scoreLevel >= 50f -> "Buen intento. Practica todos los días para mejorar tu marca."; else -> "No te rindas. La práctica diaria hace la diferencia." }
    val color   = when { scoreLevel >= 70f -> MaterialTheme.colorScheme.secondary; scoreLevel >= 50f -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.tertiary }

    Surface(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            // Emoji de resultado
            Text(
                text     = emoji,
                fontSize = 72.sp,
                modifier = Modifier.semantics { contentDescription = "Resultado: $level" }
            )

            Spacer(Modifier.height(16.dp))

            // Nombre del juego
            Text(
                text  = gameName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Puntuación grande
            Box(
                modifier         = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text  = "${"%.0f".format(score)}",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = color,
                        modifier = Modifier.semantics { contentDescription = "Puntuación: ${"%.0f".format(score)} de 100" }
                    )
                    Text(
                        text  = "/ 100",
                        style = MaterialTheme.typography.bodyLarge,
                        color = color.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Nivel de desempeño
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    text     = level,
                    style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color    = color,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Mensaje motivacional
            Text(
                text      = message,
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.weight(1f))

            // Botones de acción
            EternaFullWidthButton(
                text               = "Volver al inicio",
                onClick            = onNavigateToDashboard,
                contentDescription = "Ir al panel principal"
            )

            Spacer(Modifier.height(12.dp))

            EternaOutlinedButton(
                text               = "Jugar otro juego",
                onClick            = onPlayAgain,
                modifier           = Modifier.fillMaxWidth(),
                contentDescription = "Volver al catálogo de juegos"
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}


private fun gameNameFor(gameId: String) = when (gameId) {
    "memory_match"          -> "Memorama de Pares"
    "digit_span"            -> "Secuencia de Números"
    "flash_color"           -> "Flash de Colores"
    "trail_making"          -> "Conecta los Puntos"
    "naming_image"          -> "Nombra la Imagen"
    "verbal_fluency"        -> "Palabras en Categoría"
    "spot_diff"             -> "Encuentra Diferencias"
    "stroop"                -> "Stroop de Colores"
    "corsi_block"           -> "Reproduce el Patrón"
    "temporal_orientation"  -> "Orientación Temporal"
    "clock_drawing"         -> "Dibuja el Reloj"
    "face_name"             -> "Caras y Nombres"
    "mental_calc"           -> "Cálculo Mental"
    "prospective_memory"    -> "Memoria Prospectiva"
    "reading_comprehension" -> "Lectura y Comprensión"
    else                    -> gameId
}

@Preview(name = "Resultado Excelente", showBackground = true)
@Preview(name = "Resultado Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GameResultPreview() {
    EternaMenteTheme {
        GameResultScreen(
            innerPadding       = PaddingValues(0.dp),
            gameId             = "memory_match",
            score              = 87f,
            onNavigateToDashboard = {},
            onPlayAgain        = {}
        )
    }
}
