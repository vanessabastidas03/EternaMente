package com.eternamente.app.presentation.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Estado vacío — ilustración + mensaje + acción opcional.
 *
 * Se muestra cuando una lista o sección no tiene datos aún
 * (ej. ninguna sesión completada, sin reportes, sin logros).
 *
 * **Principios de diseño:**
 * - Tono amigable y alentador, no negativo (ej. "Aún no tienes sesiones — ¡empieza hoy!").
 * - El ícono es suave (outlined, color onSurfaceVariant) para no generar alarma.
 * - La acción opcional guía al usuario hacia el siguiente paso.
 *
 * **Accesibilidad:**
 * - El contenedor describe el estado completo a TalkBack.
 * - El ícono es decorativo (semántica en el contenedor).
 * - El botón de acción tiene `contentDescription` propio.
 *
 * @param message       Mensaje en lenguaje positivo, centrado.
 * @param modifier      Modificador externo; por defecto ocupa toda la pantalla.
 * @param icon          Ícono ilustrativo; por defecto [Icons.Outlined.FolderOpen].
 * @param actionLabel   Etiqueta del botón de acción; `null` para omitir el botón.
 * @param onAction      Lambda del botón de acción.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.FolderOpen,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics { contentDescription = message },
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,  // Semántica en el contenedor
            modifier           = Modifier.size(80.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(32.dp))
            EternaButton(
                text               = actionLabel,
                onClick            = onAction,
                contentDescription = actionLabel
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "EmptyState sin acción — Claro", showBackground = true)
@Preview(name = "EmptyState sin acción — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyStateNoActionPreview() {
    EternaMenteTheme {
        Surface {
            EmptyState(
                message = "Todavía no tienes reportes.\nCompleta tu primera sesión para verlos aquí."
            )
        }
    }
}

@Preview(name = "EmptyState con acción — Claro", showBackground = true)
@Preview(name = "EmptyState con acción — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyStateWithActionPreview() {
    EternaMenteTheme {
        Surface {
            EmptyState(
                message     = "¡Aún no has desbloqueado logros!\nCompleta sesiones para ganar medallas.",
                icon        = Icons.Outlined.SentimentNeutral,
                actionLabel = "Ir a los juegos",
                onAction    = {}
            )
        }
    }
}
