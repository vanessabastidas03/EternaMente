package com.eternamente.app.presentation.component

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.eternamente.app.navigation.Screen
import com.eternamente.app.ui.theme.EternaMenteTheme

// ══════════════════════════════════════════════════════════════════════════════
// EternaBottomNav — Barra de navegación inferior con 4 pestañas
//
// Las 4 pestañas cubren los flujos principales: inicio, juegos, reportes y perfil.
// Íconos filled (seleccionado) vs outlined (no seleccionado) para feedback visual
// claro sin depender exclusivamente del color.
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Datos de una pestaña de la [NavigationBar].
 *
 * @property screen              Destino de navegación raíz de la pestaña.
 * @property label               Etiqueta visible debajo del ícono.
 * @property selectedIcon        Ícono cuando la pestaña está activa (filled).
 * @property unselectedIcon      Ícono cuando la pestaña está inactiva (outlined).
 * @property contentDescription  Texto para TalkBack.
 */
private data class NavTab(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String
)

private val navTabs = listOf(
    NavTab(
        screen             = Screen.Dashboard,
        label              = "Inicio",
        selectedIcon       = Icons.Filled.Home,
        unselectedIcon     = Icons.Outlined.Home,
        contentDescription = "Ir al panel de inicio"
    ),
    NavTab(
        screen             = Screen.GameCatalog,
        label              = "Juegos",
        selectedIcon       = Icons.Filled.SportsEsports,
        unselectedIcon     = Icons.Outlined.SportsEsports,
        contentDescription = "Ver catálogo de juegos cognitivos"
    ),
    NavTab(
        screen             = Screen.WeeklyReport,
        label              = "Reportes",
        selectedIcon       = Icons.Filled.Assessment,
        unselectedIcon     = Icons.Outlined.Assessment,
        contentDescription = "Ver reportes de evolución cognitiva"
    ),
    NavTab(
        screen             = Screen.Profile,
        label              = "Perfil",
        selectedIcon       = Icons.Filled.Person,
        unselectedIcon     = Icons.Outlined.Person,
        contentDescription = "Ver perfil y ajustes"
    )
)

/** Rutas consideradas "activas" para cada pestaña. */
private fun selectedTabForRoute(route: String?): NavTab? = when (route) {
    Screen.Dashboard.route                                     -> navTabs[0]  // Inicio
    Screen.GameCatalog.route,
    Screen.GameInstructions.ROUTE,
    Screen.GamePlay.ROUTE                                      -> navTabs[1]  // Juegos
    Screen.WeeklyReport.route,
    Screen.MonthlyReport.route,
    Screen.AlertDetail.ROUTE                                   -> navTabs[2]  // Reportes
    Screen.Profile.route,
    Screen.Achievements.route,
    Screen.Settings.route,
    Screen.AccessibilitySettings.route                         -> navTabs[3]  // Perfil
    else                                                       -> null
}

/**
 * Barra de navegación inferior con las 4 pestañas principales de EternaMente.
 *
 * **Accesibilidad:**
 * - Cada [NavigationBarItem] tiene `contentDescription` propio.
 * - La barra completa tiene semántica de región con `contentDescription`.
 * - El estado seleccionado es anunciado por TalkBack automáticamente.
 * - Íconos filled/outlined permiten distinguir selección sin depender del color.
 *
 * **Comportamiento:**
 * - Pulsar una pestaña ya seleccionada no genera navegación redundante.
 * - El resalte de pestaña sigue sub-rutas (ej. en GamePlay → "Juegos" activo).
 *
 * @param currentRoute Ruta del destino actual, obtenida de [NavHostController].
 * @param onNavigate   Lambda con el [Screen] raíz al que navegar.
 * @param modifier     Modificador externo.
 */
@Composable
fun EternaBottomNav(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTab = selectedTabForRoute(currentRoute)

    NavigationBar(
        modifier          = modifier.semantics {
            contentDescription = "Barra de navegación principal con 4 pestañas"
        },
        containerColor    = MaterialTheme.colorScheme.surface,
        contentColor      = MaterialTheme.colorScheme.onSurface
    ) {
        navTabs.forEach { tab ->
            val isSelected = tab == selectedTab
            NavigationBarItem(
                selected = isSelected,
                onClick  = {
                    if (!isSelected) onNavigate(tab.screen)
                },
                icon     = {
                    Icon(
                        imageVector        = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.contentDescription
                    )
                },
                label    = {
                    Text(
                        text  = tab.label,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor       = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedIconColor     = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor       = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor     = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor          = MaterialTheme.colorScheme.primaryContainer
                ),
                alwaysShowLabel = true   // Siempre visible para adultos mayores
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "EternaBottomNav Inicio — Claro", showBackground = true)
@Preview(name = "EternaBottomNav Inicio — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaBottomNavDashboardPreview() {
    EternaMenteTheme {
        Surface {
            EternaBottomNav(
                currentRoute = Screen.Dashboard.route,
                onNavigate   = {}
            )
        }
    }
}

@Preview(name = "EternaBottomNav Juegos — Claro", showBackground = true)
@Composable
private fun EternaBottomNavGamesPreview() {
    EternaMenteTheme {
        Surface {
            EternaBottomNav(
                currentRoute = Screen.GameCatalog.route,
                onNavigate   = {}
            )
        }
    }
}

@Preview(name = "EternaBottomNav Perfil — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaBottomNavProfileDarkPreview() {
    EternaMenteTheme {
        Surface {
            EternaBottomNav(
                currentRoute = Screen.Profile.route,
                onNavigate   = {}
            )
        }
    }
}
