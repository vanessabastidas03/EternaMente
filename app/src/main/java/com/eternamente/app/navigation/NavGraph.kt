package com.eternamente.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.eternamente.app.presentation.auth.ConsentScreen
import com.eternamente.app.presentation.auth.LoginScreen
import com.eternamente.app.presentation.onboarding.OnboardingScreen
import com.eternamente.app.presentation.auth.RegisterScreen
import com.eternamente.app.presentation.auth.SplashScreen
import com.eternamente.app.presentation.dashboard.DashboardScreen
import com.eternamente.app.presentation.game.GameCatalogScreen
import com.eternamente.app.presentation.game.GameInstructionsScreen
import com.eternamente.app.presentation.game.GamePlayScreen
import com.eternamente.app.presentation.game.GameResultScreen
import com.eternamente.app.presentation.profile.AccessibilitySettingsScreen
import com.eternamente.app.presentation.profile.AchievementsScreen
import com.eternamente.app.presentation.profile.ProfileScreen
import com.eternamente.app.presentation.profile.SettingsScreen
import com.eternamente.app.presentation.reports.AlertDetailScreen
import com.eternamente.app.presentation.reports.MonthlyReportScreen
import com.eternamente.app.presentation.reports.PdfExportScreen
import com.eternamente.app.presentation.reports.WeeklyReportScreen

// ── Duración de animaciones ──────────────────────────────────────────────────

private const val AUTH_ANIM_MS  = 300
private const val GAME_ANIM_MS  = 280

// ── Transiciones reutilizables ───────────────────────────────────────────────

/** Entrada con desvanecimiento — flujo auth y reportes. */
private val fadeEnter  = fadeIn(tween(AUTH_ANIM_MS))
/** Salida con desvanecimiento. */
private val fadeExit   = fadeOut(tween(AUTH_ANIM_MS))

/** Entrada deslizando desde la derecha — flujo de juego (avanzar). */
private val slideEnter = slideInHorizontally(tween(GAME_ANIM_MS)) { it }
/** Salida deslizando hacia la izquierda (pantalla actual). */
private val slideExit  = slideOutHorizontally(tween(GAME_ANIM_MS)) { -it / 3 }
/** Entrada deslizando desde la izquierda — vuelta atrás en el juego. */
private val popEnter   = slideInHorizontally(tween(GAME_ANIM_MS)) { -it / 3 }
/** Salida deslizando hacia la derecha al hacer pop. */
private val popExit    = slideOutHorizontally(tween(GAME_ANIM_MS)) { it }

// ── NavGraph ─────────────────────────────────────────────────────────────────

/**
 * Grafo de navegación raíz de EternaMente.
 *
 * Gestiona el [Scaffold] que aloja la [NavigationBar] del flujo principal y
 * declara todos los destinos de la aplicación en un único [NavHost].
 *
 * **Política de back stack:**
 * - Al completar el auth flow (Splash → Dashboard), se hace `popUpTo(Splash) { inclusive=true }`
 *   para que el botón atrás no pueda volver a las pantallas de autenticación.
 * - La navegación entre pestañas usa `saveState = true` + `restoreState = true`
 *   para preservar el estado de cada sección al cambiar de pestaña.
 *
 * **Transiciones:**
 * - Flujo auth / reportes / perfil → fadeIn / fadeOut (300 ms).
 * - Flujo de juego → slideIn desde la derecha / slideOut hacia la izquierda (280 ms).
 *   El pop invierte la dirección.
 *
 * @param navController Controlador del back stack.
 * @param startDestination Ruta inicial — [Screen.Splash.route] si no hay sesión activa,
 *   [Screen.Dashboard.route] si el usuario ya está autenticado.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route
    val showBottomBar  = currentRoute in BottomNavItem.visibleRoutes

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible      = showBottomBar,
                enter        = fadeEnter,
                exit         = fadeExit,
                // Semántica de la barra para lectores de pantalla
                modifier     = Modifier.semantics {
                    contentDescription = "Barra de navegación principal"
                }
            ) {
                EternaBottomNavBar(
                    navController = navController,
                    currentRoute  = currentRoute
                )
            }
        }
    ) { innerPadding ->

        NavHost(
            navController    = navController,
            startDestination = startDestination,
            // Transición global predeterminada para auth / perfil / reportes
            enterTransition  = { fadeEnter },
            exitTransition   = { fadeExit },
            popEnterTransition = { fadeEnter },
            popExitTransition  = { fadeExit }
        ) {

            // ── Auth flow ─────────────────────────────────────────────────────

            composable(Screen.Splash.route) {
                SplashScreen(
                    innerPadding           = innerPadding,
                    onNavigateToRegister   = {
                        navController.navigate(Screen.Register.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin      = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToOnboarding = {
                        navController.navigate(Screen.Onboarding(0).navRoute()) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToDashboard  = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route     = Screen.Onboarding.ROUTE,
                arguments = listOf(navArgument("step") { type = NavType.IntType })
            ) { _ ->
                // Los 4 pasos se gestionan internamente por OnboardingViewModel.
                // El argumento {step} de la ruta se ignora (siempre se inicia desde Welcome).
                OnboardingScreen(
                    innerPadding          = innerPadding,
                    onNavigateToDashboard = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin     = {
                        navController.navigate(Screen.Login.route)
                    }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    innerPadding           = innerPadding,
                    onNavigateToLogin      = { navController.navigate(Screen.Login.route) },
                    onNavigateToOnboarding = {
                        navController.navigate(Screen.Onboarding(0).navRoute()) {
                            popUpTo(Screen.Register.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    innerPadding          = innerPadding,
                    onNavigateToDashboard = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister  = { navController.navigate(Screen.Register.route) }
                )
            }

            composable(Screen.Consent.route) {
                ConsentScreen(
                    innerPadding      = innerPadding,
                    onConsentAccepted = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // ── Main flow ─────────────────────────────────────────────────────

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    innerPadding             = innerPadding,
                    onNavigateToGameCatalog  = {
                        navController.navigate(Screen.GameCatalog.route)
                    },
                    onNavigateToReport       = {
                        navController.navigate(Screen.WeeklyReport.route)
                    },
                    onNavigateToAchievements = {
                        navController.navigate(Screen.Achievements.route)
                    },
                    onNavigateToSettings     = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToAlertDetail  = { alertId ->
                        navController.navigate(Screen.AlertDetail(alertId).navRoute())
                    }
                )
            }

            composable(Screen.GameCatalog.route) {
                GameCatalogScreen(
                    innerPadding    = innerPadding,
                    onNavigateToGame = { gameId ->
                        // Navegar directamente a GamePlay para juegos implementados;
                        // o a Instructions para el flujo estándar
                        navController.navigate(Screen.GameInstructions(gameId).navRoute())
                    }
                )
            }

            composable(
                route          = Screen.GameInstructions.ROUTE,
                arguments      = listOf(navArgument("gameId") { type = NavType.StringType }),
                enterTransition  = { slideEnter },
                exitTransition   = { slideExit },
                popEnterTransition = { popEnter },
                popExitTransition  = { popExit }
            ) { backStack ->
                val gameId = backStack.arguments?.getString("gameId").orEmpty()
                GameInstructionsScreen(
                    innerPadding    = innerPadding,
                    gameId          = gameId,
                    // onStartGame recibe (sessionId, userId) — el ViewModel crea la sesión en Room
                    onStartGame     = { sessionId, _ ->
                        navController.navigate(Screen.GamePlay(gameId, sessionId).navRoute())
                    },
                    onNavigateBack  = { navController.popBackStack() }
                )
            }

            composable(
                route          = Screen.GamePlay.ROUTE,
                arguments      = listOf(
                    navArgument("gameId")    { type = NavType.StringType },
                    navArgument("sessionId") { type = NavType.StringType }
                ),
                enterTransition  = { slideEnter },
                exitTransition   = { slideExit },
                popEnterTransition = { popEnter },
                popExitTransition  = { popExit }
            ) { backStack ->
                val gameId    = backStack.arguments?.getString("gameId").orEmpty()
                val sessionId = backStack.arguments?.getString("sessionId").orEmpty()
                GamePlayScreen(
                    innerPadding   = innerPadding,
                    gameId         = gameId,
                    sessionId      = sessionId,
                    onGameFinished = { score ->
                        navController.navigate(Screen.GameResult(gameId, score).navRoute()) {
                            popUpTo(Screen.GamePlay.ROUTE) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route          = Screen.GameResult.ROUTE,
                arguments      = listOf(
                    navArgument("gameId") { type = NavType.StringType },
                    navArgument("score")  { type = NavType.FloatType  }
                ),
                enterTransition  = { slideEnter },
                exitTransition   = { slideExit },
                popEnterTransition = { popEnter },
                popExitTransition  = { popExit }
            ) { backStack ->
                val gameId = backStack.arguments?.getString("gameId").orEmpty()
                val score  = backStack.arguments?.getFloat("score")  ?: 0f
                GameResultScreen(
                    innerPadding        = innerPadding,
                    gameId              = gameId,
                    score               = score,
                    onNavigateToDashboard = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = false }
                        }
                    },
                    onPlayAgain         = { navController.popBackStack(Screen.GameCatalog.route, false) }
                )
            }

            // ── Profile flow ──────────────────────────────────────────────────

            composable(Screen.Profile.route) {
                ProfileScreen(
                    innerPadding           = innerPadding,
                    onNavigateToAchievements = { navController.navigate(Screen.Achievements.route) },
                    onNavigateToSettings     = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.Achievements.route) {
                AchievementsScreen(
                    innerPadding   = innerPadding,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    innerPadding              = innerPadding,
                    onNavigateToAccessibility = {
                        navController.navigate(Screen.AccessibilitySettings.route)
                    },
                    onNavigateBack            = { navController.popBackStack() },
                    onLogout                  = {
                        // Clear the entire back stack so the user cannot press Back
                        // to return to the Dashboard after logging out.
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.AccessibilitySettings.route) {
                AccessibilitySettingsScreen(
                    innerPadding   = innerPadding,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Reports flow ──────────────────────────────────────────────────

            composable(Screen.WeeklyReport.route) {
                WeeklyReportScreen(
                    innerPadding            = innerPadding,
                    onNavigateToMonthly     = { navController.navigate(Screen.MonthlyReport.route) },
                    onNavigateToAlertDetail = { alertId ->
                        navController.navigate(Screen.AlertDetail(alertId).navRoute())
                    },
                    onNavigateToPdfExport   = { navController.navigate(Screen.PdfExport.route) }
                )
            }

            composable(Screen.MonthlyReport.route) {
                MonthlyReportScreen(
                    innerPadding            = innerPadding,
                    onNavigateToAlertDetail = { alertId ->
                        navController.navigate(Screen.AlertDetail(alertId).navRoute())
                    },
                    onNavigateBack          = { navController.popBackStack() }
                )
            }

            composable(Screen.PdfExport.route) {
                PdfExportScreen(
                    innerPadding   = innerPadding,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route     = Screen.AlertDetail.ROUTE,
                arguments = listOf(navArgument("alertId") { type = NavType.StringType })
            ) { backStack ->
                val alertId = backStack.arguments?.getString("alertId").orEmpty()
                AlertDetailScreen(
                    innerPadding   = innerPadding,
                    alertId        = alertId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

// ── Bottom Navigation Bar ─────────────────────────────────────────────────────

/**
 * Barra de navegación inferior con las pestañas del flujo principal.
 *
 * La pestaña activa se determina con [BottomNavItem.forRoute].
 * La navegación entre pestañas guarda y restaura el estado de cada una.
 *
 * @param navController Controlador del back stack.
 * @param currentRoute  Ruta activa en el [NavHost].
 */
@Composable
private fun EternaBottomNavBar(
    navController: NavHostController,
    currentRoute: String?
) {
    val selectedItem = BottomNavItem.forRoute(currentRoute)

    NavigationBar {
        BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
                selected = item == selectedItem,
                onClick  = {
                    if (item != selectedItem) {
                        navController.navigate(item.screen.route) {
                            // Pop al inicio del grafo para evitar acumular destinos
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                },
                icon  = {
                    Icon(
                        imageVector        = item.icon,
                        contentDescription = item.contentDescription
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}
