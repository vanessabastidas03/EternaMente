package com.eternamente.app.presentation.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Vista del perfil demográfico y estadísticas del usuario.
 *
 * Muestra: nombre, edad, educación, racha actual, total de puntos y resumen de
 * medallas desbloqueadas. Desde aquí se accede a [AchievementsScreen] y [SettingsScreen].
 *
 * @param innerPadding             Padding del [Scaffold] padre.
 * @param onNavigateToAchievements Navegar a la colección de medallas.
 * @param onNavigateToSettings     Navegar a los ajustes de la app.
 */
@Composable
fun ProfileScreen(
    innerPadding: PaddingValues,
    onNavigateToAchievements: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    PlaceholderScreen(
        screenName           = "Perfil",
        accessibilityLabel   = "Perfil del usuario con estadísticas y opciones de configuración",
        innerPadding         = innerPadding,
        primaryActionLabel   = "Ver logros",
        onPrimaryAction      = onNavigateToAchievements,
        secondaryActionLabel = "Ajustes",
        onSecondaryAction    = onNavigateToSettings
    )
}
