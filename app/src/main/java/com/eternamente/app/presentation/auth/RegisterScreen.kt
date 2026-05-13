package com.eternamente.app.presentation.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Formulario de creación de cuenta para nuevos usuarios.
 *
 * Campos requeridos (implementación pendiente):
 * - Nombre completo, edad, años de educación, género
 * - Email y contraseña
 *
 * Tras crear la cuenta en Firebase Auth, navega a [ConsentScreen] para
 * capturar el consentimiento informado antes de comenzar la primera evaluación.
 *
 * @param innerPadding         Padding del [Scaffold] padre.
 * @param onNavigateToLogin    El usuario ya tiene cuenta → ir al login.
 * @param onNavigateToConsent  Registro exitoso → mostrar formulario de consentimiento.
 */
@Composable
fun RegisterScreen(
    innerPadding: PaddingValues,
    onNavigateToLogin: () -> Unit,
    onNavigateToConsent: () -> Unit
) {
    PlaceholderScreen(
        screenName           = "Registro",
        accessibilityLabel   = "Pantalla de creación de cuenta nueva",
        innerPadding         = innerPadding,
        primaryActionLabel   = "Crear cuenta",
        onPrimaryAction      = onNavigateToConsent,
        secondaryActionLabel = "Ya tengo cuenta",
        onSecondaryAction    = onNavigateToLogin
    )
}
