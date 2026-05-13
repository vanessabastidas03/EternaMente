package com.eternamente.app.domain.model

/**
 * Jerarquía de excepciones de autenticación local.
 *
 * Son lanzadas por [RegisterUserUseCase] y [LoginUserUseCase] y capturadas
 * por [AuthViewModel] para construir mensajes de error amigables.
 * No contienen stack traces de Android para evitar leaks de información.
 */
sealed class AuthException(message: String) : Exception(message) {

    /** El correo electrónico no existe en la base de datos local. */
    object UserNotFound : AuthException("Usuario no encontrado. Verifica tu correo.")

    /**
     * El PIN ingresado no coincide con el hash almacenado.
     *
     * @property attemptsRemaining Intentos restantes antes del bloqueo.
     */
    data class InvalidPin(val attemptsRemaining: Int) :
        AuthException("PIN incorrecto. Te quedan $attemptsRemaining ${if (attemptsRemaining == 1) "intento" else "intentos"}.")

    /**
     * La cuenta está bloqueada temporalmente por demasiados intentos fallidos.
     *
     * @property minutesRemaining Minutos hasta que se desbloquea automáticamente.
     */
    data class AccountLocked(val minutesRemaining: Int) :
        AuthException("Cuenta bloqueada por seguridad. Intenta en $minutesRemaining ${if (minutesRemaining == 1) "minuto" else "minutos"}.")

    /** El correo electrónico ya está registrado en la base de datos local. */
    object EmailAlreadyExists : AuthException("Este correo ya tiene una cuenta registrada.")

    /** Formato de correo inválido. */
    object InvalidEmail : AuthException("El formato del correo no es válido.")

    /** PIN con longitud o formato incorrecto. */
    object InvalidPin6Digits : AuthException("El PIN debe tener exactamente 6 dígitos numéricos.")

    /** Los dos PINs ingresados no coinciden. */
    object PinMismatch : AuthException("Los PINs no coinciden. Vuelve a intentarlo.")
}
