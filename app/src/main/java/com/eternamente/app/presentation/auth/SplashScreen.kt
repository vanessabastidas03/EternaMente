package com.eternamente.app.presentation.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.eternamente.app.presentation.common.PlaceholderScreen
import kotlinx.coroutines.delay

/**
 * Pantalla de bienvenida animada que se muestra al iniciar la app.
 *
 * Permanece visible durante [SPLASH_DELAY_MS] milisegundos y luego decide
 * el destino de navegación según el estado de autenticación:
 * - Usuario autenticado → [onNavigateToDashboard]
 * - Sin sesión activa → [onNavigateToOnboarding]
 *
 * En una implementación real esta lógica provendría de un ViewModel
 * que observe el estado de [FirebaseAuth] a través de [UserRepository].
 *
 * @param innerPadding            Padding del [Scaffold] padre.
 * @param onNavigateToOnboarding  Navegar al primer paso del carrusel de onboarding.
 * @param onNavigateToDashboard   Navegar directamente al panel principal (sesión activa).
 */
@Composable
fun SplashScreen(
    innerPadding: PaddingValues,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    // En producción: observar FirebaseAuth.currentUser desde un ViewModel
    LaunchedEffect(Unit) {
        delay(SPLASH_DELAY_MS)
        onNavigateToOnboarding()
    }

    PlaceholderScreen(
        screenName         = "EternaMente",
        accessibilityLabel = "Pantalla de bienvenida de EternaMente, cargando…",
        innerPadding       = innerPadding,
        primaryActionLabel = "Comenzar",
        onPrimaryAction    = onNavigateToOnboarding
    )
}

private const val SPLASH_DELAY_MS = 2_000L
