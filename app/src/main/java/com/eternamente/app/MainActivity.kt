package com.eternamente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.eternamente.app.ui.theme.EternaMenteTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity host for the EternaMente Compose UI.
 *
 * Responsibilities:
 * - Enable edge-to-edge rendering (window insets handled inside composables).
 * - Provide the [NavHost] root composable that owns the back stack.
 *
 * All navigation logic and screen composables live in the `presentation` layer;
 * [MainActivity] is intentionally thin.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()          // Must be called before super.onCreate()
        super.onCreate(savedInstanceState)
        setContent {
            EternaMenteTheme {
                val navController = rememberNavController()
                EternaNavHost(navController = navController)
            }
        }
    }
}

/**
 * Root navigation graph for EternaMente.
 *
 * All route strings are defined in [NavRoutes] to avoid stringly-typed errors.
 * Screen composables are added here as each feature module is implemented.
 *
 * @param navController The [NavHostController] that manages the back stack.
 */
@Composable
fun EternaNavHost(navController: NavHostController) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = NavRoutes.HOME,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(NavRoutes.HOME) {
                // Placeholder — replaced by HomeScreen composable in presentation layer
                Box(
                    modifier        = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "EternaMente",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }
            composable(NavRoutes.LOGIN) {
                // LoginScreen — to be implemented
            }
            composable(NavRoutes.REGISTER) {
                // RegisterScreen — to be implemented
            }
            composable(NavRoutes.REPORT) {
                // ReportScreen — to be implemented
            }
            composable(NavRoutes.PROFILE) {
                // ProfileScreen — to be implemented
            }
        }
    }
}

/**
 * Compile-time-safe navigation route constants.
 *
 * Route strings are centralised here to prevent typo-based navigation failures.
 * Arguments are embedded as `{argName}` segments following Navigation Compose conventions.
 */
object NavRoutes {
    const val HOME     = "home"
    const val LOGIN    = "login"
    const val REGISTER = "register"
    const val REPORT   = "report"
    const val PROFILE  = "profile"
    const val GAME     = "game/{gameId}"

    /** Builds the deep-link route for a specific mini-game. */
    fun game(gameId: String) = "game/$gameId"
}
