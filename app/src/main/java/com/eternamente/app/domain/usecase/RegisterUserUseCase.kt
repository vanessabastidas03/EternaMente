package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.crypto.CryptoManager
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.AuthException
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.model.UserCredentials
import com.eternamente.app.domain.repository.AuthRepository
import com.eternamente.app.domain.repository.GamificationRepository
import com.eternamente.app.domain.repository.UserRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Caso de uso para el registro de un nuevo usuario con autenticación local por PIN.
 *
 * **Flujo de registro:**
 * 1. Validar nombre, correo y PIN (formato y unicidad).
 * 2. Crear [User] con datos mínimos (perfil demográfico se completa en onboarding).
 * 3. Generar salt aleatorio y hash PBKDF2-SHA256 del PIN.
 * 4. Persistir [User] y [UserCredentials] en Room (SQLCipher AES-256).
 * 5. Inicializar perfil de gamificación.
 * 6. Almacenar `userId` en DataStore para el flujo de onboarding.
 *
 * **Seguridad:**
 * - El PIN NUNCA sale de esta función en texto plano.
 * - El hash se calcula con PBKDF2WithHmacSHA256, 100 000 iteraciones, salt 256 bits.
 * - El array del PIN se limpia de memoria con `spec.clearPassword()` en [CryptoManager].
 *
 * @property userRepository          Persiste el [User] en Room.
 * @property authRepository          Persiste las credenciales hasheadas.
 * @property gamificationRepository  Inicializa el perfil de gamificación.
 * @property cryptoManager           Genera salt y hashea el PIN.
 * @property userPreferencesRepository Persiste el `userId` activo en DataStore.
 */
class RegisterUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val gamificationRepository: GamificationRepository,
    private val cryptoManager: CryptoManager,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private val EMAIL_REGEX =
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        const val PIN_LENGTH = 6
    }

    /**
     * Registra un nuevo usuario.
     *
     * @param name         Nombre completo (no vacío, solo letras y espacios).
     * @param email        Correo electrónico (formato válido, no registrado previamente).
     * @param pin          PIN de 6 dígitos numéricos (texto plano — se hashea aquí).
     * @param confirmPin   Debe ser igual a [pin].
     * @return [Result.Success] con el [User] creado, o [Result.Error] con [AuthException].
     */
    suspend operator fun invoke(
        name: String,
        email: String,
        pin: String,
        confirmPin: String
    ): Result<User> = safeCall {
        val trimmedName  = name.trim()
        val trimmedEmail = email.lowercase().trim()

        // ── Validaciones de dominio ───────────────────────────────────────────
        require(trimmedName.isNotBlank()) {
            throw AuthException.InvalidPin6Digits // Reutiliza la exc; la VM usa el mensaje
        }
        if (!EMAIL_REGEX.matches(trimmedEmail)) throw AuthException.InvalidEmail
        if (pin.length != PIN_LENGTH || !pin.all { it.isDigit() }) throw AuthException.InvalidPin6Digits
        if (pin != confirmPin) throw AuthException.PinMismatch

        // Unicidad de correo
        val existing = userRepository.getUserByEmail(trimmedEmail)
        if (existing is Result.Success && existing.data != null) throw AuthException.EmailAlreadyExists

        // ── Crear usuario mínimo (perfil se completa en onboarding) ───────────
        val userId = UUID.randomUUID().toString()
        val now    = System.currentTimeMillis()
        val user   = User(
            id             = userId,
            email          = trimmedEmail,
            name           = trimmedName,
            age            = 0,           // Completado en Onboarding
            educationYears = 0,           // Completado en Onboarding
            gender         = "",          // Completado en Onboarding
            createdAt      = now,
            consentGivenAt = null         // Registrado en Onboarding
        )
        userRepository.registerUser(user).getOrThrow()

        // ── Hashear PIN y guardar credenciales ────────────────────────────────
        val salt    = cryptoManager.generateSalt()
        val pinHash = cryptoManager.hashPin(pin, salt)
        val credentials = UserCredentials(
            userId               = userId,
            pinHash              = pinHash,
            pinSalt              = salt,
            failedLoginAttempts  = 0,
            lockedUntil          = null
        )
        authRepository.saveCredentials(credentials).getOrThrow()

        // ── Inicializar gamificación ──────────────────────────────────────────
        gamificationRepository.initializeProfile(userId).getOrThrow()

        // ── Marcar usuario activo en DataStore ────────────────────────────────
        userPreferencesRepository.updateCurrentUserId(userId)

        user
    }

    private fun <T> Result<T>.getOrThrow(): T = when (this) {
        is Result.Success -> data
        is Result.Error   -> throw exception
    }
}
