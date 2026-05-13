package com.eternamente.app.presentation.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Pantalla de consentimiento informado, obligatoria antes de cualquier evaluación cognitiva.
 *
 * Muestra el documento de consentimiento completo y requiere aceptación explícita.
 * Al aceptar, se llama a [UserRepository.recordConsent] con el timestamp actual
 * y se navega al panel principal.
 *
 * **Restricción de privacidad:** ningún dato cognitivo puede recogerse mientras
 * [com.eternamente.app.domain.model.User.consentGivenAt] sea `null`.
 *
 * @param innerPadding     Padding del [Scaffold] padre.
 * @param onConsentAccepted El usuario aceptó el consentimiento → navegar al panel principal.
 */
@Composable
fun ConsentScreen(
    innerPadding: PaddingValues,
    onConsentAccepted: () -> Unit
) {
    PlaceholderScreen(
        screenName         = "Consentimiento informado",
        accessibilityLabel = "Pantalla de consentimiento informado, lea el documento y acepte para continuar",
        innerPadding       = innerPadding,
        primaryActionLabel = "Acepto y continúo",
        onPrimaryAction    = onConsentAccepted
    )
}
