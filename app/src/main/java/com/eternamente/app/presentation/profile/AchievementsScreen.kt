package com.eternamente.app.presentation.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Galería de [Badge] desbloqueados y pendientes.
 *
 * Muestra las 10 medallas definidas en [com.eternamente.app.domain.model.Badge]
 * con su estado (obtenida / bloqueada), fecha de obtención y descripción.
 *
 * @param innerPadding   Padding del [Scaffold] padre.
 * @param onNavigateBack Volver al perfil.
 */
@Composable
fun AchievementsScreen(
    innerPadding: PaddingValues,
    onNavigateBack: () -> Unit
) {
    PlaceholderScreen(
        screenName           = "Logros y medallas",
        accessibilityLabel   = "Galería de medallas obtenidas y por desbloquear",
        innerPadding         = innerPadding,
        primaryActionLabel   = "Volver al perfil",
        onPrimaryAction      = onNavigateBack
    )
}
