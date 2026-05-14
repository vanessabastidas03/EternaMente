package com.eternamente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.eternamente.app.navigation.NavGraph
import com.eternamente.app.navigation.Screen
import com.eternamente.app.presentation.main.MainViewModel
import com.eternamente.app.ui.theme.AccessibilityConfig
import com.eternamente.app.ui.theme.EternaMenteTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity de EternaMente (Single-Activity Architecture).
 *
 * ## Integración de tema reactivo
 *
 * El flujo es el siguiente:
 * ```
 * UserPreferencesRepository (DataStore)
 *   └── Flow<UserPreferences>
 *         └── MainViewModel.preferences: StateFlow<UserPreferences>
 *               └── collectAsState() en setContent
 *                     └── EternaMenteTheme(darkTheme, highContrast)
 *                           └── LightCS / DarkCS / HCLightCS / HCDarkCS
 * ```
 *
 * Cuando [OnboardingViewModel] escribe en DataStore con `updateDarkMode()` o
 * `updateHighContrast()`, el Flow emite inmediatamente, [MainViewModel] lo propaga
 * como [StateFlow], `collectAsState()` invalida la composición y Compose
 * re-ejecuta [EternaMenteTheme] con el esquema correcto. **Sin `recreate()`.**
 *
 * ## Inicio en el destino correcto
 *
 * El `startDestination` se calcula una sola vez con `remember` para que no cambie
 * ante recomposiciones. El check de FirebaseAuth es síncrono (caché local del SDK)
 * y no bloquea el hilo principal.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // ── Recolectar preferencias del usuario desde DataStore ────────────
            val mainViewModel: MainViewModel = hiltViewModel()
            val prefs by mainViewModel.preferences.collectAsState()

            // ── Aplicar tema reactivo — sin recreate() ─────────────────────────
            EternaMenteTheme(
                darkTheme           = prefs.darkMode,
                highContrast        = prefs.highContrast,
                fontScale           = prefs.fontScale,
                accessibilityConfig = AccessibilityConfig(
                    hapticFeedback = prefs.hapticFeedback,
                    reducedMotion  = prefs.reducedMotion
                )
            ) {
                val navController = rememberNavController()

                // startDestination calculado una sola vez; FirebaseAuth.currentUser
                // es síncrono (caché local del SDK, sin I/O).
                val startDestination = remember {
                    if (FirebaseAuth.getInstance().currentUser != null)
                        Screen.Dashboard.route
                    else
                        Screen.Splash.route
                }

                NavGraph(
                    navController    = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
