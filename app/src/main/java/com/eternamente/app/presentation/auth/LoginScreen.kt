package com.eternamente.app.presentation.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Formulario de inicio de sesión para usuarios registrados.
 *
 * Campos requeridos (implementación pendiente): email, contraseña.
 *
 * En caso de éxito, navega a [com.eternamente.app.navigation.Screen.Dashboard]
 * eliminando el flujo de auth del back stack (`popUpTo(Splash, inclusive=true)`),
 * de modo que el botón atrás no pueda volver a la pantalla de login.
 *
 * @param innerPadding           Padding del [Scaffold] padre.
 * @param onNavigateToDashboard  Login exitoso → navegar al panel principal.
 * @param onNavigateToRegister   El usuario no tiene cuenta → ir al registro.
 */
@Composable
fun LoginScreen(
    innerPadding: PaddingValues,
    onNavigateToDashboard: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    PlaceholderScreen(
        screenName           = "Iniciar sesión",
        accessibilityLabel   = "Pantalla de inicio de sesión con email y contraseña",
        innerPadding         = innerPadding,
        primaryActionLabel   = "Entrar",
        onPrimaryAction      = onNavigateToDashboard,
        secondaryActionLabel = "Crear cuenta",
        onSecondaryAction    = onNavigateToRegister
    )
}
