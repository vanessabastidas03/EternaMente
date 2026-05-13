package com.eternamente.app.presentation.dashboard

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Panel principal de EternaMente — punto de entrada tras la autenticación.
 *
 * Contenido previsto (implementación pendiente):
 * - Resumen del estado cognitivo actual ([AlertLevel] y [riskScore]).
 * - Racha de sesiones y puntos de gamificación.
 * - Acceso rápido al catálogo de juegos.
 * - Notificaciones de alertas pendientes.
 *
 * @param innerPadding            Padding del [Scaffold] del [NavGraph] (incluye altura del BottomBar).
 * @param onNavigateToGameCatalog Navegar al catálogo de juegos cognitivos.
 */
@Composable
fun DashboardScreen(
    innerPadding: PaddingValues,
    onNavigateToGameCatalog: () -> Unit
) {
    PlaceholderScreen(
        screenName         = "Panel principal",
        accessibilityLabel = "Panel principal con resumen de tu estado cognitivo y acceso a juegos",
        innerPadding       = innerPadding,
        primaryActionLabel = "Ver juegos cognitivos",
        onPrimaryAction    = onNavigateToGameCatalog
    )
}
