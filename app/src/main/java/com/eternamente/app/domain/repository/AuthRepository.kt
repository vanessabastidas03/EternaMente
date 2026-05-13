package com.eternamente.app.domain.repository

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.UserCredentials

/**
 * Contrato para la gestión de credenciales de autenticación local.
 *
 * Las implementaciones viven en la capa de datos y coordinan con Room+SQLCipher.
 * La capa de dominio NUNCA accede directamente a hashes o salts — eso es
 * responsabilidad de [CryptoManager] en la capa de datos.
 */
interface AuthRepository {

    /**
     * Persiste las credenciales hasheadas de un nuevo usuario.
     *
     * @param credentials Credenciales con PIN ya hasheado (nunca texto plano).
     */
    suspend fun saveCredentials(credentials: UserCredentials): Result<Unit>

    /**
     * Obtiene las credenciales de un usuario por su UUID.
     *
     * @param userId UUID del [com.eternamente.app.domain.model.User].
     * @return Las credenciales o `null` si no existen.
     */
    suspend fun getCredentialsByUserId(userId: String): Result<UserCredentials?>

    /**
     * Actualiza los contadores de intentos fallidos y el timestamp de bloqueo.
     *
     * @param userId         UUID del usuario.
     * @param attempts       Nuevo conteo de intentos fallidos.
     * @param lockedUntilMs  Epoch-ms de desbloqueo automático; `null` = sin bloqueo.
     */
    suspend fun updateFailedAttempts(
        userId: String,
        attempts: Int,
        lockedUntilMs: Long?
    ): Result<Unit>

    /**
     * Elimina las credenciales de un usuario (usada en `deleteAccount`).
     *
     * @param userId UUID del usuario a eliminar.
     */
    suspend fun deleteCredentials(userId: String): Result<Unit>
}
