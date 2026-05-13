package com.eternamente.app.presentation.dashboard.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.presentation.component.EternaCard
import com.eternamente.app.ui.theme.EternaMenteTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Widget de resumen semanal del Dashboard.
 *
 * Muestra 7 círculos representando los últimos 7 días:
 * - **Verde con checkmark**: sesión completada ese día.
 * - **Gris vacío**: sin sesión.
 *
 * Debajo de cada círculo aparece la inicial del día de la semana en español.
 *
 * @param weekProgress      Lista de 7 booleanos. Índice 0 = hace 6 días, índice 6 = hoy.
 * @param completionPct     Porcentaje de días completados esta semana (0–100).
 * @param modifier          Modificador externo.
 */
@Composable
fun WeeklySummaryWidget(
    weekProgress: List<Boolean>,
    completionPct: Int,
    modifier: Modifier = Modifier
) {
    // Calcular las etiquetas de día para cada posición (0 = hace 6 días, 6 = hoy)
    val dayLabels = remember {
        val today    = LocalDate.now(ZoneId.systemDefault())
        val locale   = Locale("es")
        (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
                .replaceFirstChar { it.uppercase() }
        }
    }

    EternaCard(
        modifier           = modifier.fillMaxWidth(),
        contentDescription = "Resumen semanal: $completionPct% de días completados"
    ) {
        Column {
            // ── Título y porcentaje ───────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "Esta semana",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text  = "$completionPct%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── 7 círculos ────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                weekProgress.forEachIndexed { index, completed ->
                    val label     = dayLabels.getOrElse(index) { "" }
                    val isToday   = index == weekProgress.size - 1
                    val bgColor   = when {
                        completed -> MaterialTheme.colorScheme.secondary
                        isToday   -> MaterialTheme.colorScheme.primaryContainer
                        else      -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val dayDesc   = if (completed) "Día $label completado" else "Día $label sin sesión"

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.semantics { contentDescription = dayDesc }
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (completed) {
                                Icon(
                                    imageVector        = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint               = MaterialTheme.colorScheme.onSecondary,
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text  = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Semana parcial — Claro", showBackground = true)
@Preview(name = "Semana parcial — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeeklySummaryWidgetPartialPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            WeeklySummaryWidget(
                weekProgress  = listOf(true, true, false, true, true, false, true),
                completionPct = 71
            )
        }
    }
}

@Preview(name = "Semana vacía — Claro", showBackground = true)
@Composable
private fun WeeklySummaryWidgetEmptyPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            WeeklySummaryWidget(
                weekProgress  = List(7) { false },
                completionPct = 0
            )
        }
    }
}
