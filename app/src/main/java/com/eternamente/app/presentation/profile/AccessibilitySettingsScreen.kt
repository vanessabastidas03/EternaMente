package com.eternamente.app.presentation.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Ajustes de accesibilidad de EternaMente.
 *
 * Opciones previstas:
 * - Tamaño de texto (escala de SP).
 * - Contraste alto (ajusta [MaterialTheme.colorScheme]).
 * - Retroalimentación háptica en juegos.
 * - Modo daltónico (paleta de colores alternativa).
 * - Reducir animaciones (respeta el ajuste del sistema).
 *
 * Los valores se persisten en [DataStore] con [EncryptedSharedPreferences].
 *
 * @param innerPadding   Padding del [Scaffold] padre.
 * @param onNavigateBack Volver a [SettingsScreen].
 */
@Composable
fun AccessibilitySettingsScreen(
    innerPadding: PaddingValues,
    onNavigateBack: () -> Unit
) {
    PlaceholderScreen(
        screenName         = "Accesibilidad",
        accessibilityLabel = "Ajustes de accesibilidad: tamaño de texto, contraste y animaciones",
        innerPadding       = innerPadding,
        primaryActionLabel = "Volver a ajustes",
        onPrimaryAction    = onNavigateBack
    )
}
