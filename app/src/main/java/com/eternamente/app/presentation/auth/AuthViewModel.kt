package com.eternamente.app.presentation.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.AuthException
import com.eternamente.app.domain.repository.UserRepository
import com.eternamente.app.domain.usecase.LoginUserUseCase
import com.eternamente.app.domain.usecase.RegisterUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel compartido por [RegisterScreen] y [LoginScreen].
 *
 * Expone dos [StateFlow] independientes para no mezclar el estado de registro
 * con el de login. Cada pantalla observa el que le corresponde.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val registerUserUseCase: RegisterUserUseCase,
    private val loginUserUseCase: LoginUserUseCase,
    private val userRepository: UserRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // ── Register ──────────────────────────────────────────────────────────────

    private val _registerState = MutableStateFlow(RegisterState())
    val registerState: StateFlow<RegisterState> = _registerState.asStateFlow()

    fun onRegisterNameChanged(name: String) {
        val error = if (name.trim().length < 2) "Mínimo 2 letras" else null
        _registerState.update { it.copy(name = name, nameError = error) }
    }

    fun onRegisterEmailChanged(email: String) {
        val error = if (email.isNotBlank() && !isValidEmail(email)) "Correo no válido" else null
        _registerState.update { it.copy(email = email, emailError = error) }
    }

    fun onRegisterPinChanged(pin: String) {
        // Rechazar entrada no numérica o mayor a 6 dígitos
        if (pin.length > 6 || !pin.all { it.isDigit() }) return

        // Revalidar el campo de confirmación cuando el PIN principal cambia.
        // Sin esto, un usuario que corrige el PIN principal después de haber
        // introducido la confirmación dejará canSubmit en estado incorrecto.
        val currentConfirm = _registerState.value.confirmPin
        val confirmError = if (currentConfirm.isNotBlank() && currentConfirm != pin) {
            "Los PINs no coinciden"
        } else null

        _registerState.update {
            it.copy(pin = pin, pinError = null, confirmPinError = confirmError)
        }
    }

    fun onRegisterConfirmPinChanged(confirm: String) {
        if (confirm.length > 6 || !confirm.all { it.isDigit() }) return
        val error = if (confirm.isNotBlank() && confirm != _registerState.value.pin) {
            "Los PINs no coinciden"
        } else null
        _registerState.update { it.copy(confirmPin = confirm, confirmPinError = error) }
    }

    fun register() {
        val s = _registerState.value
        _registerState.update { it.copy(isLoading = true, globalError = null) }
        viewModelScope.launch {
            when (val result = registerUserUseCase(s.name, s.email, s.pin, s.confirmPin)) {
                is com.eternamente.app.core.Result.Success ->
                    _registerState.update { it.copy(isLoading = false, isSuccess = true) }
                is com.eternamente.app.core.Result.Error ->
                    _registerState.update {
                        it.copy(isLoading = false, globalError = result.exception.message)
                    }
            }
        }
    }

    fun clearRegisterError() = _registerState.update { it.copy(globalError = null) }

    // ── Login ─────────────────────────────────────────────────────────────────

    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    init {
        checkBiometricAvailability()
    }

    fun onLoginEmailChanged(email: String) {
        val error = if (email.isNotBlank() && !isValidEmail(email)) "Correo no válido" else null
        _loginState.update { it.copy(email = email, emailError = error, globalError = null) }
    }

    fun onLoginPinChanged(pin: String) {
        if (pin.length > 6 || !pin.all { it.isDigit() }) return
        _loginState.update { it.copy(pin = pin, pinError = null, globalError = null) }
    }

    fun login() {
        val s = _loginState.value
        _loginState.update { it.copy(isLoading = true, globalError = null) }
        viewModelScope.launch {
            when (val result = loginUserUseCase(s.email, s.pin)) {
                is com.eternamente.app.core.Result.Success ->
                    _loginState.update { it.copy(isLoading = false, isSuccess = true) }
                is com.eternamente.app.core.Result.Error -> {
                    val (error, remaining, locked, minutes) = parseAuthError(result.exception)
                    _loginState.update {
                        it.copy(
                            isLoading          = false,
                            globalError        = error,
                            attemptsRemaining  = remaining,
                            isLocked           = locked,
                            minutesUntilUnlock = minutes,
                            pin                = ""   // Limpiar PIN tras error
                        )
                    }
                }
            }
        }
    }

    /**
     * Llamado cuando la biometría tiene éxito: recupera el userId activo
     * y marca el login como exitoso sin verificar PIN.
     */
    fun onBiometricAuthSuccess() {
        viewModelScope.launch {
            val userId = userPreferencesRepository.getCurrentUserId()
            if (userId != null) {
                _loginState.update { it.copy(isSuccess = true) }
            } else {
                _loginState.update {
                    it.copy(globalError = "No se encontró la cuenta. Inicia sesión con tu PIN.")
                }
            }
        }
    }

    fun clearLoginError() = _loginState.update {
        it.copy(globalError = null, attemptsRemaining = null)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun checkBiometricAvailability() {
        val manager = BiometricManager.from(appContext)
        val available = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
        _loginState.update { it.copy(isBiometricAvailable = available) }
    }

    private data class AuthErrorResult(
        val message: String,
        val remaining: Int?,
        val locked: Boolean,
        val minutes: Int
    )

    private fun parseAuthError(exception: Exception): AuthErrorResult = when (exception) {
        is AuthException.InvalidPin   ->
            AuthErrorResult(exception.message ?: "", exception.attemptsRemaining, false, 0)
        is AuthException.AccountLocked ->
            AuthErrorResult(exception.message ?: "", null, true, exception.minutesRemaining)
        else ->
            AuthErrorResult(exception.message ?: "Error de autenticación", null, false, 0)
    }

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private fun isValidEmail(email: String) = EMAIL_REGEX.matches(email)
}
