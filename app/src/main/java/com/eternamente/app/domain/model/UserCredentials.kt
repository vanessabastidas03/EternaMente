package com.eternamente.app.domain.model

/**
 * Credenciales de autenticación local del usuario.
 *
 * El PIN NUNCA se almacena en texto plano. Solo se persiste el hash derivado
 * con PBKDF2-SHA256 y su salt aleatorio. La verificación de PIN se hace
 * comparando hashes con [MessageDigest.isEqual] (tiempo constante).
 *
 * @property userId            UUID del [User] propietario.
 * @property pinHash           Hash PBKDF2-SHA256 del PIN codificado en Base64.
 * @property pinSalt           Salt aleatorio de 32 bytes codificado en Base64.
 * @property failedLoginAttempts Intentos fallidos consecutivos desde el último éxito.
 * @property lockedUntil       Epoch-ms hasta el que la cuenta está bloqueada; `null` si no está bloqueada.
 */
data class UserCredentials(
    val userId: String,
    val pinHash: String,
    val pinSalt: String,
    val failedLoginAttempts: Int  = 0,
    val lockedUntil: Long?        = null
) {
    /** `true` si la cuenta está actualmente bloqueada por intentos fallidos. */
    fun isLocked(now: Long = System.currentTimeMillis()): Boolean =
        lockedUntil != null && now < lockedUntil

    /** Minutos restantes de bloqueo, redondeados hacia arriba. `0` si no hay bloqueo. */
    fun minutesRemainingLocked(now: Long = System.currentTimeMillis()): Int {
        if (lockedUntil == null || now >= lockedUntil) return 0
        return ((lockedUntil - now) / 60_000L).toInt() + 1
    }
}
