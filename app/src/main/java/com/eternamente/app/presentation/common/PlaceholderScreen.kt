package com.eternamente.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Composable genérico de marcador de posición compartido por todas las pantallas
 * mientras se implementa su lógica real.
 *
 * Garantiza:
 * - Nombre de pantalla visible en tipografía [MaterialTheme.typography.displaySmall].
 * - Botón de navegación principal con [onPrimaryAction].
 * - Botón secundario opcional con [onSecondaryAction].
 * - [contentDescription] en el contenedor raíz para accesibilidad (TalkBack).
 *
 * @param screenName          Nombre que se muestra en pantalla.
 * @param accessibilityLabel  Descripción para lectores de pantalla.
 * @param innerPadding        Padding del [Scaffold] padre.
 * @param primaryActionLabel  Etiqueta del botón principal.
 * @param onPrimaryAction     Lambda del botón principal.
 * @param secondaryActionLabel Etiqueta del botón secundario; si es `null`, no se muestra.
 * @param onSecondaryAction   Lambda del botón secundario.
 */
@Composable
fun PlaceholderScreen(
    screenName: String,
    accessibilityLabel: String,
    innerPadding: PaddingValues,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
            .semantics { contentDescription = accessibilityLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text      = screenName,
            style     = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = "Pantalla en construcción",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(onClick = onPrimaryAction) {
            Text(primaryActionLabel)
        }

        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSecondaryAction) {
                Text(secondaryActionLabel)
            }
        }
    }
}
