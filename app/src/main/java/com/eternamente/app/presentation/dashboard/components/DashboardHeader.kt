package com.eternamente.app.presentation.dashboard.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.ui.theme.EternaMenteTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Encabezado personalizado del Dashboard.
 *
 * Muestra un saludo según la hora del sistema y la fecha en formato amigable en español.
 * Todos los cálculos de fecha/hora se ejecutan dentro de [remember] para evitar
 * recomposiciones innecesarias — solo se recalculan si el composable sale y vuelve
 * a entrar en la composición.
 *
 * @param userName  Primer nombre del usuario (ej. "Ana"). Si está vacío, omite el nombre.
 * @param modifier  Modificador externo.
 */
@Composable
fun DashboardHeader(
    userName: String,
    modifier: Modifier = Modifier
) {
    val greeting = remember {
        when (LocalTime.now(ZoneId.systemDefault()).hour) {
            in 5..11  -> "Buenos días"
            in 12..17 -> "Buenas tardes"
            else      -> "Buenas noches"
        }
    }

    val dateText = remember {
        val today     = LocalDate.now(ZoneId.systemDefault())
        val locale    = Locale("es")
        val dayName   = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { it.uppercase() }
        val dayNum    = today.dayOfMonth
        val monthName = today.month.getDisplayName(TextStyle.FULL, locale)
        "$dayName, $dayNum de $monthName"
    }

    val greetingFull = if (userName.isNotBlank()) "¡$greeting, $userName!" else "¡$greeting!"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .semantics {
                contentDescription = "$greetingFull Hoy es $dateText"
            }
    ) {
        Text(
            text  = greetingFull,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = dateText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Header — Claro", showBackground = true)
@Preview(name = "Header — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardHeaderPreview() {
    EternaMenteTheme {
        Surface { DashboardHeader(userName = "Ana") }
    }
}

@Preview(name = "Header sin nombre — Claro", showBackground = true)
@Composable
private fun DashboardHeaderNoNamePreview() {
    EternaMenteTheme {
        Surface { DashboardHeader(userName = "") }
    }
}
