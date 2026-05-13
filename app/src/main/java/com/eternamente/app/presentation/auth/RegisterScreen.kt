package com.eternamente.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.component.EternaTextField
import com.eternamente.app.presentation.component.EternaTextButton
import com.eternamente.app.presentation.component.PinInputField

/**
 * Pantalla de registro de cuenta con autenticación local por PIN.
 *
 * ## Flujo de navegación por teclado
 * 1. Nombre → (Next) → Correo → (Next) → PIN principal
 * 2. PIN principal → (Next) → PIN de confirmación
 * 3. PIN de confirmación → (Done) → cierra el teclado
 *
 * El traspaso de foco entre PIN1 y PIN2 se gestiona con [FocusRequester]:
 * - `confirmPinFocusRequester` es creado en este composable y pasado a [PinInputField].
 * - Cuando el usuario pulsa `Next` en el PIN principal, `confirmPinFocusRequester.requestFocus()`
 *   mueve el foco al campo de confirmación y abre el teclado numérico en él.
 *
 * ## Estado
 * [AuthViewModel.registerState] expone [RegisterState] como StateFlow.
 * El botón se habilita cuando [RegisterState.canSubmit] es `true`.
 */
@Composable
fun RegisterScreen(
    innerPadding: PaddingValues,
    onNavigateToLogin: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    val viewModel       = hiltViewModel<AuthViewModel>()
    val state           by viewModel.registerState.collectAsState()
    val keyboard        = LocalSoftwareKeyboardController.current

    // FocusRequester para el campo de confirmación de PIN.
    // Creado aquí (no en PinInputField) para poder pasárselo como callback
    // al primer campo PIN a través de onImeAction.
    val confirmPinFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onNavigateToOnboarding()
    }

    Surface(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .semantics { contentDescription = "Pantalla de creación de cuenta" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            Spacer(Modifier.height(12.dp))

            // ── Logo ──────────────────────────────────────────────────────────
            Icon(
                imageVector        = Icons.Filled.Psychology,
                contentDescription = "Logo de EternaMente",
                modifier           = Modifier.height(72.dp),
                tint               = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text      = "Crear cuenta",
                style     = MaterialTheme.typography.headlineLarge,
                color     = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text      = "Solo tú tendrás acceso a tus datos",
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // ── Nombre ────────────────────────────────────────────────────────
            EternaTextField(
                value             = state.name,
                onValueChange     = viewModel::onRegisterNameChanged,
                label             = "Nombre completo",
                isError           = state.nameError != null,
                supportingText    = state.nameError,
                accessibilityHint = "Campo de nombre completo",
                imeAction         = ImeAction.Next
            )

            Spacer(Modifier.height(16.dp))

            // ── Correo ────────────────────────────────────────────────────────
            EternaTextField(
                value             = state.email,
                onValueChange     = viewModel::onRegisterEmailChanged,
                label             = "Correo electrónico",
                isError           = state.emailError != null,
                supportingText    = state.emailError,
                keyboardType      = KeyboardType.Email,
                accessibilityHint = "Campo de correo electrónico",
                imeAction         = ImeAction.Next
            )

            Spacer(Modifier.height(24.dp))

            // ── PIN principal ─────────────────────────────────────────────────
            Text(
                text     = "Elige tu PIN de 6 dígitos",
                style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color    = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text     = "Lo usarás para entrar a la app cada día",
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            PinInputField(
                pin               = state.pin,
                onPinChanged      = viewModel::onRegisterPinChanged,
                isError           = state.pinError != null,
                // ImeAction.Next + onImeAction mueve el foco al campo de confirmación
                imeAction         = ImeAction.Next,
                onImeAction       = { confirmPinFocusRequester.requestFocus() },
                contentDescription = "PIN de seguridad, ${state.pin.length} de 6 dígitos ingresados"
            )

            if (state.pinError != null) {
                Text(
                    text     = state.pinError!!,
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Confirmar PIN ─────────────────────────────────────────────────
            Text(
                text     = "Confirma tu PIN",
                style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color    = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            PinInputField(
                pin               = state.confirmPin,
                onPinChanged      = viewModel::onRegisterConfirmPinChanged,
                isError           = state.confirmPinError != null,
                // Recibe el FocusRequester externo para que PIN1 pueda moverle el foco
                focusRequester    = confirmPinFocusRequester,
                imeAction         = ImeAction.Done,
                onImeAction       = { keyboard?.hide() },
                contentDescription = "Confirmación de PIN, ${state.confirmPin.length} de 6 dígitos ingresados"
            )

            if (state.confirmPinError != null) {
                Text(
                    text     = state.confirmPinError!!,
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            // ── Error global ──────────────────────────────────────────────────
            if (state.globalError != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text      = state.globalError!!,
                    style     = MaterialTheme.typography.bodyLarge,
                    color     = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Error: ${state.globalError}" }
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Botón principal ───────────────────────────────────────────────
            EternaFullWidthButton(
                text               = "Crear cuenta",
                onClick            = { viewModel.register() },
                enabled            = state.canSubmit,
                isLoading          = state.isLoading,
                contentDescription = if (state.canSubmit) "Crear cuenta"
                                     else "Completa todos los campos para continuar"
            )

            Spacer(Modifier.height(16.dp))

            EternaTextButton(
                text               = "¿Ya tienes cuenta? Iniciar sesión",
                onClick            = onNavigateToLogin,
                contentDescription = "Ir a la pantalla de inicio de sesión"
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
