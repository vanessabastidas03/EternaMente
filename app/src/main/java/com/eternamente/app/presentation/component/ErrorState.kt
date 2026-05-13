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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Estado de error — ícono de advertencia + mensaje + botón de reintento.
 *
 * **Principios de diseño para adultos mayores:**
 * - Mensajes directos en lenguaje no técnico (ej. "No se pudo cargar" en lugar
 *   de "Error HTTP 503").
 * - El botón de reintento es grande (≥ 56 dp) y claramente etiquetado.
 * - El ícono de advertencia usa [MaterialTheme.colorScheme.error] para señal visual
 *   inmediata, sin ser alarmante (no es rojo intenso en la paleta EternaMente).
 *
 * **Accesibilidad:**
 * - El contenedor anuncia `"Error: $message"` a TalkBack al aparecer.
 * - El ícono de error es decorativo (semántica en el contenedor).
 * - El botón de reintento es pulsable con D-pad y tiene `contentDescription`.
 *
 * @param message    Mensaje de error en lenguaje sencillo.
 * @param onRetry    Lambda invocada al pulsar el botón de reintento.
 * @param modifier   Modificador externo; por defecto ocupa toda la pantalla.
 * @param retryLabel Etiqueta del botón (por defecto "Reintentar").
 * @param cause      Detalle técnico opcional para mostrar en modo debug; `null` para ocultar.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Reintentar",
    cause: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics { contentDescription = "Error: $message" },
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Filled.Warning,
            contentDescription = null,  // Semántica en el contenedor
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (cause != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text      = cause,
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(32.dp))

        EternaButton(
            text               = retryLabel,
            onClick            = onRetry,
            contentDescription = "$retryLabel la acción que falló"
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "ErrorState — Claro", showBackground = true)
@Preview(name = "ErrorState — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ErrorStatePreview() {
    EternaMenteTheme {
        Surface {
            ErrorState(
                message = "No se pudo cargar tu historial.\nVerifica tu conexión a internet.",
                onRetry = {}
            )
        }
    }
}

@Preview(name = "ErrorState con causa — Claro", showBackground = true)
@Composable
private fun ErrorStateWithCausePreview() {
    EternaMenteTheme {
        Surface {
            ErrorState(
                message = "No se pudo guardar el resultado del juego.",
                onRetry = {},
                cause   = "Error de base de datos: no hay espacio disponible"
            )
        }
    }
}
