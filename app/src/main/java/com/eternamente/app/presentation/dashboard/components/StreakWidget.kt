package com.eternamente.app.presentation.dashboard.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.eternamente.app.presentation.component.EternaCard
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Widget de racha del Dashboard.
 *
 * Muestra el número de días consecutivos del usuario con una animación Lottie
 * de llama cuando la racha es ≥ [MIN_STREAK_FOR_ANIMATION].
 *
 * **Animación Lottie:**
 * Requiere el archivo `assets/lottie/flame.json`. Si el archivo no existe,
 * cae a un emoji 🔥 como fallback (la composición Lottie devuelve `null`).
 *
 * @param streak   Número de días consecutivos. 0 muestra el estado "sin racha".
 * @param modifier Modificador externo.
 */
@Composable
fun StreakWidget(
    streak: Int,
    modifier: Modifier = Modifier
) {
    EternaCard(
        modifier           = modifier.fillMaxWidth(),
        contentDescription = if (streak > 0) "$streak días seguidos de entrenamiento"
                             else "Todavía no tienes racha. Completa una sesión para comenzar."
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(4.dp)
        ) {
            // ── Icono: Lottie si streak ≥ 3, emoji si no ──────────────────────
            if (streak >= MIN_STREAK_FOR_ANIMATION) {
                LottieFlame(modifier = Modifier.size(64.dp))
            } else {
                Text(
                    text     = if (streak > 0) "🔥" else "💪",
                    fontSize = 48.sp,
                    modifier = Modifier
                        .size(64.dp)
                        .semantics { contentDescription = if (streak > 0) "Llama de racha" else "Músculo animado" }
                )
            }

            Spacer(Modifier.width(16.dp))

            // ── Texto de racha ─────────────────────────────────────────────────
            Column {
                if (streak > 0) {
                    Text(
                        text  = "$streak",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text  = "${if (streak == 1) "día seguido" else "días seguidos"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text  = "Comienza tu racha hoy",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text  = "Completa una sesión para activarla",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Animación Lottie de llama con bucle infinito.
 * Fallback: si `assets/lottie/flame.json` no existe, la composición es `null`
 * y no se renderiza nada — el llamador mostrará el emoji como fallback.
 */
@Composable
private fun LottieFlame(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/flame.json")
    )
    val progress by animateLottieCompositionAsState(
        composition  = composition,
        iterations   = LottieConstants.IterateForever,
        isPlaying    = true,
        clipSpec     = LottieClipSpec.Progress(0f, 1f)
    )

    if (composition != null) {
        LottieAnimation(
            composition = composition,
            progress    = { progress },
            modifier    = modifier
        )
    } else {
        // Fallback si el archivo .json no está en assets/
        Text(text = "🔥", fontSize = 48.sp, modifier = modifier)
    }
}

private const val MIN_STREAK_FOR_ANIMATION = 7

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Racha 0 — Claro", showBackground = true)
@Preview(name = "Racha 0 — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreakWidgetZeroPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) { StreakWidget(streak = 0) }
    }
}

@Preview(name = "Racha 1 — Claro", showBackground = true)
@Composable
private fun StreakWidgetOnePreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) { StreakWidget(streak = 1) }
    }
}

@Preview(name = "Racha 7 — Claro", showBackground = true)
@Preview(name = "Racha 7 — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreakWidgetSevenPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) { StreakWidget(streak = 7) }
    }
}
