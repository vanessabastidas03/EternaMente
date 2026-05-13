package com.eternamente.app.presentation.auth

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.presentation.component.EternaButton
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.component.EternaTextField
import com.eternamente.app.presentation.component.EternaTextButton
import com.eternamente.app.presentation.component.PinInputField

/**
 * Pantalla de inicio de sesión con PIN local y autenticación biométrica opcional.
 *
 * **Flujo de biometría:**
 * - Si el dispositivo tiene biometría configurada, se muestra un botón prominente.
 * - Al pulsar, [BiometricPrompt] verifica con la huella/Face ID del sistema.
 * - En caso de éxito, [AuthViewModel.onBiometricAuthSuccess] recupera el userId
 *   de DataStore y completa el login sin verificar PIN.
 *
 * **Política de intentos fallidos:**
 * - Hasta [LoginUserUseCase.MAX_FAILED_ATTEMPTS] (5) intentos.
 * - Tras el 5° intento: cuenta bloqueada 30 minutos.
 * - El mensaje de error muestra los intentos restantes.
 */
@Composable
fun LoginScreen(
    innerPadding: PaddingValues,
    onNavigateToDashboard: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val viewModel: AuthViewModel = hiltViewModel()
    val state by viewModel.loginState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onNavigateToDashboard()
    }

    // BiometricPrompt — requiere FragmentActivity
    val biometricPrompt = remember(context) {
        val activity = context as? FragmentActivity ?: return@remember null
        val executor = ContextCompat.getMainExecutor(context)
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricAuthSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        // Ignorar cancelaciones voluntarias del usuario
                    }
                }
                override fun onAuthenticationFailed() { /* Intentos individuales fallidos */ }
            }
        )
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Acceso biométrico")
            .setSubtitle("Usa tu huella o Face ID para entrar a EternaMente")
            .setNegativeButtonText("Usar PIN")
            .build()
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
                .semantics { contentDescription = "Pantalla de inicio de sesión" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            Spacer(Modifier.height(24.dp))

            // ── Logo ──────────────────────────────────────────────────────────
            Icon(
                imageVector        = Icons.Filled.Psychology,
                contentDescription = "Logo de EternaMente",
                modifier           = Modifier.size(88.dp),
                tint               = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = "EternaMente",
                style     = MaterialTheme.typography.headlineLarge,
                color     = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text      = "Bienvenida de vuelta",
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ── Botón biométrico (solo si está disponible) ─────────────────────
            if (state.isBiometricAvailable && biometricPrompt != null) {
                EternaButton(
                    text               = "Entrar con huella / Face ID",
                    onClick            = { biometricPrompt.authenticate(promptInfo) },
                    modifier           = Modifier.fillMaxWidth(),
                    contentDescription = "Autenticarse con biometría: huella digital o reconocimiento facial",
                    leadingIcon        = Icons.Filled.Fingerprint
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text  = "— o usa tu PIN —",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))
            }

            // ── Correo ────────────────────────────────────────────────────────
            EternaTextField(
                value             = state.email,
                onValueChange     = viewModel::onLoginEmailChanged,
                label             = "Correo electrónico",
                isError           = state.emailError != null,
                supportingText    = state.emailError,
                keyboardType      = KeyboardType.Email,
                accessibilityHint = "Campo de correo electrónico para iniciar sesión"
            )

            Spacer(Modifier.height(24.dp))

            // ── PIN ───────────────────────────────────────────────────────────
            Text(
                text     = "Tu PIN de 6 dígitos",
                style    = MaterialTheme.typography.titleMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            PinInputField(
                pin               = state.pin,
                onPinChanged      = viewModel::onLoginPinChanged,
                isError           = state.globalError != null,
                contentDescription = "PIN de acceso, ${state.pin.length} de 6 dígitos ingresados"
            )

            // ── Errores ───────────────────────────────────────────────────────
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

                if (state.isLocked) {
                    Text(
                        text      = "La cuenta se desbloqueará en ${state.minutesUntilUnlock} minutos",
                        style     = MaterialTheme.typography.bodyLarge,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Botón principal ───────────────────────────────────────────────
            EternaFullWidthButton(
                text               = "Entrar",
                onClick            = { viewModel.login() },
                enabled            = state.canSubmit,
                isLoading          = state.isLoading,
                contentDescription = when {
                    state.isLocked  -> "Cuenta bloqueada temporalmente"
                    state.canSubmit -> "Iniciar sesión"
                    else            -> "Completa el correo y el PIN para entrar"
                }
            )

            Spacer(Modifier.height(16.dp))

            EternaTextButton(
                text               = "¿No tienes cuenta? Regístrate",
                onClick            = onNavigateToRegister,
                contentDescription = "Ir a la pantalla de registro de cuenta nueva"
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
