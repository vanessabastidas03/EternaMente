package com.eternamente.app.presentation.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Ajustes generales de EternaMente.
 *
 * Secciones previstas:
 * - Notificaciones (FCM) — frecuencia y horario.
 * - Privacidad — exportar / borrar datos.
 * - Accesibilidad → navega a [AccessibilitySettingsScreen].
 * - Cerrar sesión.
 *
 * @param innerPadding                Padding del [Scaffold] padre.
 * @param onNavigateToAccessibility   Navegar a ajustes de accesibilidad.
 * @param onNavigateBack              Volver al perfil.
 */
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onNavigateToAccessibility: () -> Unit,
    onNavigateBack: () -> Unit
) {
    PlaceholderScreen(
        screenName           = "Ajustes",
        accessibilityLabel   = "Pantalla de ajustes de la aplicación",
        innerPadding         = innerPadding,
        primaryActionLabel   = "Accesibilidad",
        onPrimaryAction      = onNavigateToAccessibility,
        secondaryActionLabel = "Volver",
        onSecondaryAction    = onNavigateBack
    )
}
