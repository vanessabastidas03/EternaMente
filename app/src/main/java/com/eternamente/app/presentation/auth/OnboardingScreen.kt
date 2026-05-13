package com.eternamente.app.presentation.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Carrusel de onboarding que presenta la propuesta de valor de EternaMente.
 *
 * Cada paso es un destino independiente en el back stack, lo que permite
 * retroceder paso a paso con el botón atrás del sistema.
 *
 * **Pasos definidos:**
 * - 0 → Presentación de la aplicación y sus beneficios
 * - 1 → Explicación de los juegos cognitivos
 * - 2 → Privacidad y seguridad de los datos
 * - ≥ [ONBOARDING_TOTAL_STEPS] → Redirige al formulario de registro
 *
 * @param innerPadding         Padding del [Scaffold] padre.
 * @param step                 Paso actual (0-based), obtenido del argumento de navegación.
 * @param onNextStep           Avanzar al siguiente paso.
 * @param onNavigateToRegister Ir al formulario de registro (último paso completado).
 * @param onNavigateToLogin    Ir al login si el usuario ya tiene cuenta.
 */
@Composable
fun OnboardingScreen(
    innerPadding: PaddingValues,
    step: Int,
    onNextStep: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val isLastStep = step >= ONBOARDING_TOTAL_STEPS - 1
    val primaryAction = if (isLastStep) onNavigateToRegister else onNextStep

    PlaceholderScreen(
        screenName           = "Onboarding\nPaso ${step + 1} de $ONBOARDING_TOTAL_STEPS",
        accessibilityLabel   = "Pantalla de introducción, paso ${step + 1} de $ONBOARDING_TOTAL_STEPS",
        innerPadding         = innerPadding,
        primaryActionLabel   = if (isLastStep) "Crear cuenta" else "Siguiente",
        onPrimaryAction      = primaryAction,
        secondaryActionLabel = "Ya tengo cuenta",
        onSecondaryAction    = onNavigateToLogin
    )
}

/** Número total de pasos del carrusel de onboarding. */
private const val ONBOARDING_TOTAL_STEPS = 3
