package com.eternamente.app.presentation.auth

// ══════════════════════════════════════════════════════════════════════════════
// Estado de pantalla de REGISTRO
// ══════════════════════════════════════════════════════════════════════════════

data class RegisterState(
    val name: String            = "",
    val nameError: String?      = null,
    val email: String           = "",
    val emailError: String?     = null,
    val pin: String             = "",
    val pinError: String?       = null,
    val confirmPin: String      = "",
    val confirmPinError: String? = null,
    val isLoading: Boolean      = false,
    val isSuccess: Boolean      = false,
    val globalError: String?    = null
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() && email.isNotBlank() &&
            pin.length == 6 && confirmPin.length == 6 &&
            nameError == null && emailError == null &&
            pinError == null && confirmPinError == null &&
            !isLoading
}

// ══════════════════════════════════════════════════════════════════════════════
// Estado de pantalla de LOGIN
// ══════════════════════════════════════════════════════════════════════════════

data class LoginState(
    val email: String               = "",
    val emailError: String?         = null,
    val pin: String                 = "",
    val pinError: String?           = null,
    val isLoading: Boolean          = false,
    val isSuccess: Boolean          = false,
    val globalError: String?        = null,
    /** `true` si el hardware biométrico está disponible y configurado. */
    val isBiometricAvailable: Boolean = false,
    /** Intentos restantes antes del bloqueo; `null` si no hay error de PIN aún. */
    val attemptsRemaining: Int?     = null,
    /** `true` si la cuenta está bloqueada temporalmente. */
    val isLocked: Boolean           = false,
    /** Minutos hasta el desbloqueo automático. */
    val minutesUntilUnlock: Int     = 0
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && pin.length == 6 && !isLoading && !isLocked
}
