package com.eternamente.app.presentation.dashboard.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Tarjeta de alerta cognitiva — visible solo cuando [MlPrediction.alertLevel]
 * es [AlertLevel.ALERT] (rojo) o [AlertLevel.WATCH] (naranja/amarillo).
 *
 * La tarjeta usa el color del tema [MaterialTheme.colorScheme.errorContainer] para
 * ALERT y [MaterialTheme.colorScheme.tertiaryContainer] para WATCH, garantizando
 * que el contraste sea adecuado en ambos modos de color (claro y oscuro).
 *
 * @param alert          La predicción ML con nivel de alerta activo.
 * @param onViewDetails  Callback al pulsar "Ver detalles".
 * @param modifier       Modificador externo.
 */
@Composable
fun CognitiveAlertCard(
    alert: MlPrediction,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (containerColor, borderColor, iconTint) = when (alert.alertLevel) {
        AlertLevel.ALERT -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error
        )
        AlertLevel.WATCH -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiary
        )
        AlertLevel.NORMAL -> return  // No mostrar tarjeta para nivel normal
    }

    val alertTitle = when (alert.alertLevel) {
        AlertLevel.ALERT -> "Atención: cambio cognitivo detectado"
        AlertLevel.WATCH -> "Seguimiento recomendado"
        else             -> return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$alertTitle: ${alert.explanation}"
            },
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(width = 1.5.dp, color = borderColor)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector        = Icons.Filled.Warning,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(28.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = alertTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text     = alert.explanation,
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(4.dp))

            TextButton(
                onClick  = onViewDetails,
                modifier = Modifier.semantics { contentDescription = "Ver detalles de la alerta" }
            ) {
                Text(
                    text  = "Ver\ndetalles",
                    style = MaterialTheme.typography.labelLarge,
                    color = borderColor
                )
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

private val previewAlert = MlPrediction(
    id             = "alert-1",
    userId         = "user-1",
    predictionDate = System.currentTimeMillis(),
    riskScore      = 0.72f,
    alertLevel     = AlertLevel.WATCH,
    domainsFlagged = listOf(
        com.eternamente.app.domain.model.CognitiveDomain.MEMORY,
        com.eternamente.app.domain.model.CognitiveDomain.ATTENTION
    ),
    explanation    = "Se observa una tendencia descendente en memoria y atención. Mantén la frecuencia de sesiones."
)

@Preview(name = "Alerta WATCH — Claro", showBackground = true)
@Preview(name = "Alerta WATCH — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CognitiveAlertCardWatchPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            CognitiveAlertCard(alert = previewAlert, onViewDetails = {})
        }
    }
}

@Preview(name = "Alerta ALERT — Claro", showBackground = true)
@Composable
private fun CognitiveAlertCardAlertPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            CognitiveAlertCard(
                alert = previewAlert.copy(
                    alertLevel  = AlertLevel.ALERT,
                    explanation = "Cambios significativos detectados en memoria, atención y funciones ejecutivas. Consulte con un profesional de salud."
                ),
                onViewDetails = {}
            )
        }
    }
}
