package com.eternamente.app.presentation.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.eternamente.app.navigation.Screen
import com.eternamente.app.ui.theme.EternaMenteTheme

// ══════════════════════════════════════════════════════════════════════════════
// EternaScaffold — Scaffold base de EternaMente
//
// Combina EternaTopBar + EternaBottomNav + FAB opcional en un único componente
// reutilizable. Úsalo en pantallas que necesiten su propio scaffold (por ejemplo,
// en arquitecturas sin NavGraph o en módulos de features independientes).
//
// Nota: cuando se usa NavGraph.kt con su Scaffold global, no es necesario
// envolver cada pantalla en EternaScaffold. Esto se destina a uso standalone.
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Scaffold base de EternaMente con [EternaTopBar] y [EternaBottomNav] integrados.
 *
 * Combina los componentes de navegación en un único punto de entrada,
 * evitando que cada pantalla duplique la configuración de TopBar y BottomBar.
 *
 * @param title              Título mostrado en el [EternaTopBar].
 * @param currentRoute       Ruta activa para resaltar la pestaña correcta en [EternaBottomNav].
 * @param onNavigate         Lambda de navegación para las pestañas del [EternaBottomNav].
 * @param modifier           Modificador externo del Scaffold.
 * @param onNavigateBack     Si no es `null`, muestra el botón de retroceso en la TopBar.
 * @param showBottomNav      `false` oculta la barra inferior (ej. en juego activo o auth).
 * @param topBarActions      Bloque de íconos de acción en el extremo derecho de la TopBar.
 * @param floatingActionButton FAB opcional; usa [FabPosition.End] por defecto.
 * @param floatingActionButtonPosition Posición del FAB en la pantalla.
 * @param content            Contenido de la pantalla; recibe los [PaddingValues] del scaffold.
 */
@Composable
fun EternaScaffold(
    title: String,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    showBottomNav: Boolean = true,
    topBarActions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier    = modifier,
        topBar      = {
            EternaTopBar(
                title          = title,
                onNavigateBack = onNavigateBack,
                actions        = topBarActions
            )
        },
        bottomBar   = {
            if (showBottomNav) {
                EternaBottomNav(
                    currentRoute = currentRoute,
                    onNavigate   = onNavigate
                )
            }
        },
        floatingActionButton         = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        content     = content
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "EternaScaffold Dashboard — Claro", showBackground = true)
@Preview(name = "EternaScaffold Dashboard — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaScaffoldDashboardPreview() {
    EternaMenteTheme {
        EternaScaffold(
            title        = "Panel principal",
            currentRoute = Screen.Dashboard.route,
            onNavigate   = {}
        ) {
            Surface { Text("Contenido de la pantalla") }
        }
    }
}

@Preview(name = "EternaScaffold con retroceso — Claro", showBackground = true)
@Composable
private fun EternaScaffoldWithBackPreview() {
    EternaMenteTheme {
        EternaScaffold(
            title          = "Instrucciones",
            currentRoute   = Screen.GameInstructions.ROUTE,
            onNavigate     = {},
            onNavigateBack = {},
            showBottomNav  = false
        ) {
            Surface { Text("Instrucciones del juego") }
        }
    }
}
