package com.eternamente.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pestañas de la barra de navegación inferior del flujo principal.
 *
 * Cada ítem mapea a la pantalla raíz de su sección y lleva los metadatos
 * de accesibilidad necesarios para el [androidx.compose.material3.NavigationBar].
 *
 * La visibilidad de la barra se controla en [NavGraph] comparando la ruta
 * actual contra [visibleRoutes].
 */
enum class BottomNavItem(
    /** Pantalla raíz que abre la pestaña al pulsarla. */
    val screen: Screen,
    /** Icono mostrado en la barra. */
    val icon: ImageVector,
    /** Etiqueta visible debajo del icono. */
    val label: String,
    /** Descripción para TalkBack / lectores de pantalla. */
    val contentDescription: String
) {

    HOME(
        screen             = Screen.Dashboard,
        icon               = Icons.Filled.Home,
        label              = "Inicio",
        contentDescription = "Ir al panel de inicio"
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

        /**
         * Conjunto de rutas donde la [NavigationBar] debe ser visible.
         *
         * Incluye las raíces de cada sección y sus sub-pantallas de primer nivel,
         * pero excluye las pantallas de juego activo (experiencia de pantalla completa)
         * y las pantallas de auth.
         */
        val visibleRoutes: Set<String> = setOf(
            // HOME tab
            Screen.Dashboard.route,
            Screen.GameCatalog.route,
            // REPORTS tab
            Screen.WeeklyReport.route,
            Screen.MonthlyReport.route,
            // PROFILE tab
            Screen.Profile.route,
            Screen.Achievements.route,
            Screen.Settings.route,
            Screen.AccessibilitySettings.route
        )

        /**
         * Devuelve el [BottomNavItem] cuya pestaña debe aparecer seleccionada
         * para la ruta activa [route], o `null` si la barra no aplica.
         */
        fun forRoute(route: String?): BottomNavItem? = when (route) {
            Screen.Dashboard.route,
            Screen.GameCatalog.route            -> HOME
            Screen.WeeklyReport.route,
            Screen.MonthlyReport.route          -> REPORTS
            Screen.Profile.route,
            Screen.Achievements.route,
            Screen.Settings.route,
            Screen.AccessibilitySettings.route  -> PROFILE
            else                                -> null
        }
    }
}
