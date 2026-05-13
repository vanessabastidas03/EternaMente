package com.eternamente.app.presentation.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Estado de carga — spinner centrado con mensaje descriptivo.
 *
 * **Accesibilidad:**
 * - El contenedor usa `liveRegion = LiveRegionMode.Polite` para que TalkBack
 *   anuncie el [message] cuando el estado de carga aparece sin interrupción.
 * - El [CircularProgressIndicator] es puramente decorativo (no tiene rol de botón).
 * - El [message] describe QUÉ se está cargando para evitar mensajes genéricos.
 *
 * @param message  Descripción de la operación en curso (ej. "Cargando tu historial…").
 * @param modifier Modificador externo; por defecto ocupa toda la pantalla.
 */
@Composable
fun LoadingState(
    message: String = "Cargando…",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics {
                contentDescription = message
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier    = Modifier.size(56.dp),
            color       = MaterialTheme.colorScheme.primary,
            trackColor  = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 5.dp
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "LoadingState — Claro", showBackground = true)
@Preview(name = "LoadingState — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoadingStatePreview() {
    EternaMenteTheme {
        Surface {
            LoadingState(message = "Cargando tu historial cognitivo…")
        }
    }
}
