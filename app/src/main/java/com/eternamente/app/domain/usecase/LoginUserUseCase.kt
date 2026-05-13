package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.crypto.CryptoManager
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.AuthException
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.repository.AuthRepository
import com.eternamente.app.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Caso de uso para la autenticación local con correo + PIN.
 *
 * **Flujo de login:**
 * 1. Buscar usuario por correo en Room.
 * 2. Cargar sus [com.eternamente.app.domain.model.UserCredentials].
 * 3. Verificar si la cuenta está bloqueada (`lockedUntil`).
 * 4. Comparar hash PBKDF2-SHA256 del PIN ingresado con el almacenado.
 * 5a. **PIN correcto** → resetear intentos fallidos, almacenar `userId` en DataStore.
 * 5b. **PIN incorrecto** → incrementar intentos; al llegar a [MAX_FAILED_ATTEMPTS]
 *     bloquear la cuenta durante [LOCKOUT_DURATION_MS] milisegundos.
 *
 * **Política de bloqueo:**
 * - Máximo [MAX_FAILED_ATTEMPTS] intentos consecutivos.
 * - Tras el último intento fallido: cuenta bloqueada 30 minutos.
 * - El bloqueo se levanta automáticamente cuando `lockedUntil < System.currentTimeMillis()`.
 * - Un login exitoso resetea el contador a 0.
 *
 * @property userRepository          Busca el [User] por correo.
 * @property authRepository          Lee y actualiza las credenciales.
 * @property cryptoManager           Verifica el hash del PIN.
 * @property userPreferencesRepository Persiste el `userId` activo en DataStore.
 */
class LoginUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val cryptoManager: CryptoManager,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    companion object {
        const val MAX_FAILED_ATTEMPTS  = 5
        const val LOCKOUT_DURATION_MS  = 30L * 60_000L  // 30 minutos en ms
    }

    /**
     * Autentica al usuario con [email] y [pin].
     *
     * @param email Correo electrónico (se normaliza a minúsculas sin espacios).
     * @param pin   PIN de 6 dígitos en texto plano.
     * @return [Result.Success] con el [User] autenticado, o [Result.Error] con [AuthException].
     */
    suspend operator fun invoke(email: String, pin: String): Result<User> = safeCall {
        val trimmedEmail = email.lowercase().trim()

        // ── 1. Buscar usuario ─────────────────────────────────────────────────
        val user = userRepository.getUserByEmail(trimmedEmail).getOrThrow()
            ?: throw AuthException.UserNotFound

        // ── 2. Cargar credenciales ────────────────────────────────────────────
        val credentials = authRepository.getCredentialsByUserId(user.id).getOrThrow()
            ?: throw AuthException.UserNotFound

        // ── 3. Verificar bloqueo ──────────────────────────────────────────────
        val now = System.currentTimeMillis()
        if (credentials.isLocked(now)) {
            throw AuthException.AccountLocked(credentials.minutesRemainingLocked(now))
        }

        // ── 4. Verificar PIN (comparación en tiempo constante) ────────────────
        val isValid = cryptoManager.verifyPin(pin, credentials.pinSalt, credentials.pinHash)

        if (!isValid) {
            val newAttempts    = credentials.failedLoginAttempts + 1
            val newLockedUntil = if (newAttempts >= MAX_FAILED_ATTEMPTS) now + LOCKOUT_DURATION_MS else null
            authRepository.updateFailedAttempts(user.id, newAttempts, newLockedUntil).getOrThrow()

            throw if (newLockedUntil != null) {
                AuthException.AccountLocked(30)
            } else {
                AuthException.InvalidPin(MAX_FAILED_ATTEMPTS - newAttempts)
            }
        }

        // ── 5a. Login exitoso ─────────────────────────────────────────────────
        if (credentials.failedLoginAttempts > 0) {
            authRepository.updateFailedAttempts(user.id, 0, null).getOrThrow()
        }
        userPreferencesRepository.updateCurrentUserId(user.id)
        // Marcar sesión como activa → Splash irá a Dashboard en el próximo inicio
        userPreferencesRepository.updateIsLoggedIn(true)

        user
    }

    private fun <T> Result<T>.getOrThrow(): T = when (this) {
        is Result.Success -> data
        is Result.Error   -> throw exception
    }
}
