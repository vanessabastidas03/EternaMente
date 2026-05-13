package com.eternamente.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

enum class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
    val contentDescription: String
) {
    HOME(
        screen             = Screen.Dashboard,
        icon               = Icons.Filled.Home,
        label              = "Inicio",
        contentDescription = "Ir al panel de inicio"
    ),
    GAMES(
        screen             = Screen.GameCatalog,
        icon               = Icons.Filled.SportsEsports,
        label              = "Juegos",
        contentDescription = "Ver catálogo de juegos cognitivos"
    ),
    REPORTS(
        screen             = Screen.WeeklyReport,
        icon               = Icons.Filled.Assessment,
        label              = "Reportes",
        contentDescription = "Ver reportes de evolución cognitiva"
    ),
    PROFILE(
        screen             = Screen.Profile,
        icon               = Icons.Filled.Person,
        label              = "Perfil",
        contentDescription = "Ver y editar perfil de usuario"
    );

    companion object {
        val visibleRoutes: Set<String> = setOf(
            Screen.Dashboard.route,
            Screen.GameCatalog.route,
            Screen.GameInstructions.ROUTE,
            Screen.WeeklyReport.route,
            Screen.MonthlyReport.route,
            Screen.Profile.route,
            Screen.Achievements.route,
            Screen.Settings.route,
            Screen.AccessibilitySettings.route
        )

        fun forRoute(route: String?): BottomNavItem? = when (route) {
            Screen.Dashboard.route                                          -> HOME
            Screen.GameCatalog.route, Screen.GameInstructions.ROUTE        -> GAMES
            Screen.WeeklyReport.route, Screen.MonthlyReport.route          -> REPORTS
            Screen.Profile.route, Screen.Achievements.route,
            Screen.Settings.route, Screen.AccessibilitySettings.route      -> PROFILE
            else                                                            -> null
        }
    }
}
