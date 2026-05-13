package com.eternamente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.eternamente.app.navigation.NavGraph
import com.eternamente.app.navigation.Screen
import com.eternamente.app.ui.theme.EternaMenteTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity de EternaMente (Single-Activity Architecture).
 *
 * Responsabilidades:
 * - Habilitar el modo edge-to-edge (insets gestionados por los composables hoja).
 * - Determinar el destino de inicio según el estado de autenticación.
 * - Instanciar el [NavGraph] raíz dentro del tema de la aplicación.
 *
 * **startDestination**: se evalúa una única vez con [remember] para evitar
 * re-composición al rotar la pantalla.
 * - [FirebaseAuth.currentUser] != null → [Screen.Dashboard] (sesión activa)
 * - null → [Screen.Splash] (flujo de auth)
 *
 * En producción esta lógica puede moverse a un `MainViewModel` que observe
 * el [UserRepository.observeCurrentUser] Flow y exponga el destino de inicio
 * como un `StateFlow`.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            EternaMenteTheme {
                val navController = rememberNavController()

                // Evaluado una única vez — no reactivo a cambios de auth en tiempo real.
                // La reactividad se implementará con MainViewModel + collectAsState().
                val startDestination = remember {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        Screen.Dashboard.route
                    } else {
                        Screen.Splash.route
                    }
                }

                NavGraph(
                    navController    = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
