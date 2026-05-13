package com.eternamente.app.presentation.dashboard.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.presentation.component.EternaCard
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Fila de acceso rápido del Dashboard con 3 tarjetas compactas.
 *
 * Cada tarjeta es pulsable y navega a su sección correspondiente.
 *
 * @param onNavigateToReport        Navegar al reporte semanal.
 * @param onNavigateToAchievements  Navegar a logros/medallas.
 * @param onNavigateToSettings      Navegar a ajustes.
 * @param modifier                  Modificador externo.
 */
@Composable
fun QuickAccessRow(
    onNavigateToReport: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickAccessCard(
            icon               = Icons.Filled.ShowChart,
            label              = "Ver mi progreso",
            contentDescription = "Ir a reportes de progreso cognitivo",
            onClick            = onNavigateToReport,
            modifier           = Modifier.weight(1f)
        )
        QuickAccessCard(
            icon               = Icons.Filled.EmojiEvents,
            label              = "Mis logros",
            contentDescription = "Ir a la galería de medallas y logros",
            onClick            = onNavigateToAchievements,
            modifier           = Modifier.weight(1f)
        )
        QuickAccessCard(
            icon               = Icons.Filled.Settings,
            label              = "Configuración",
            contentDescription = "Ir a ajustes de la aplicación",
            onClick            = onNavigateToSettings,
            modifier           = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickAccessCard(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EternaCard(
        modifier           = modifier,
        contentDescription = contentDescription,
        onClick            = onClick
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,  // Semántica en EternaCard
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelLarge,
                color     = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Acceso rápido — Claro", showBackground = true)
@Preview(name = "Acceso rápido — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QuickAccessRowPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            QuickAccessRow(
                onNavigateToReport       = {},
                onNavigateToAchievements = {},
                onNavigateToSettings     = {}
            )
        }
    }
}
