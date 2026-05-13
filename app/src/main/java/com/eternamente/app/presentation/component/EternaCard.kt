package com.eternamente.app.presentation.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.ui.theme.EternaMenteTheme

// ══════════════════════════════════════════════════════════════════════════════
// EternaCard — Variantes de tarjeta para EternaMente
//
// Especificaciones de diseño:
// - Padding interno: 16 dp (uniforme en todos los lados).
// - Esquinas: 12 dp (heredado del Shape theme via MaterialTheme.shapes.medium).
// - [contentDescription] debe describir el propósito COMPLETO de la tarjeta
//   para usuarios de TalkBack (no solo el título visible).
// ══════════════════════════════════════════════════════════════════════════════

private val CardInternalPadding = 16.dp

// ── Elevated (uso principal) ───────────────────────────────────────────────────

/**
 * Tarjeta elevada — variante principal de EternaMente.
 *
 * La elevación (sombra) ayuda a los usuarios con visión reducida a distinguir
 * las tarjetas del fondo sin necesitar colores distintos.
 *
 * @param modifier            Modificador externo.
 * @param contentDescription  Descripción completa para TalkBack.
 * @param onClick             Si no es `null`, la tarjeta es pulsable (accesible con D-pad).
 * @param content             Contenido interno; recibe un [ColumnScope].
 */
@Composable
fun EternaCard(
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val semanticsMod = if (contentDescription.isNotEmpty()) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier

    if (onClick != null) {
        ElevatedCard(
            onClick   = onClick,
            modifier  = semanticsMod,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(CardInternalPadding), content = content)
        }
    } else {
        ElevatedCard(
            modifier  = semanticsMod,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(CardInternalPadding), content = content)
        }
    }
}

// ── Filled (uso secundario) ────────────────────────────────────────────────────

/**
 * Tarjeta rellena — variante para contenido de menos jerarquía visual.
 *
 * Usa el color [MaterialTheme.colorScheme.surfaceVariant] como fondo,
 * creando separación visual sin necesitar sombra.
 */
@Composable
fun EternaFilledCard(
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val semanticsMod = if (contentDescription.isNotEmpty()) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier

    if (onClick != null) {
        Card(
            onClick  = onClick,
            modifier = semanticsMod,
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(CardInternalPadding), content = content)
        }
    } else {
        Card(
            modifier = semanticsMod,
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(CardInternalPadding), content = content)
        }
    }
}

// ── Outlined (uso terciario) ───────────────────────────────────────────────────

/**
 * Tarjeta con borde — variante para contenido informativo estructurado.
 *
 * El borde ayuda a delimitar el contenido sin añadir peso visual.
 * Útil en listas donde se necesita separar ítems sin sombras.
 */
@Composable
fun EternaOutlinedCard(
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val semanticsMod = if (contentDescription.isNotEmpty()) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier

    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = semanticsMod) {
            Column(modifier = Modifier.padding(CardInternalPadding), content = content)
        }
    } else {
        OutlinedCard(modifier = semanticsMod) {
            Column(modifier = Modifier.padding(CardInternalPadding), content = content)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "EternaCard — Claro", showBackground = true)
@Preview(name = "EternaCard — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaCardPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            EternaCard(
                modifier           = Modifier.fillMaxWidth(),
                contentDescription = "Tarjeta de puntuación: memoria, 85 de 100"
            ) {
                Text("Dominio: Memoria", style = MaterialTheme.typography.titleMedium)
                Text("Puntuación: 85 / 100", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Preview(name = "EternaCard pulsable — Claro", showBackground = true)
@Composable
private fun EternaCardClickablePreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            EternaCard(
                modifier           = Modifier.fillMaxWidth(),
                contentDescription = "Juego Stroop, pulsa para ver instrucciones",
                onClick            = {}
            ) {
                Text("Juego: Stroop", style = MaterialTheme.typography.titleMedium)
                Text("Entrena tu atención selectiva", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "EternaOutlinedCard — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaOutlinedCardDarkPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            EternaOutlinedCard(
                modifier           = Modifier.fillMaxWidth(),
                contentDescription = "Resultado semanal"
            ) {
                Text("Semana del 6 al 12 de mayo", style = MaterialTheme.typography.titleSmall)
                Text("3 de 5 sesiones completadas", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
