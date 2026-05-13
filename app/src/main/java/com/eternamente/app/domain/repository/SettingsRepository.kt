package com.eternamente.app.domain.repository

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Contrato para los ajustes de uso de la app vinculados a la cuenta del usuario.
 *
 * Estos ajustes viven en Room (no en DataStore) para que se restauren
 * automáticamente al iniciar sesión, independientemente del dispositivo.
 */
interface SettingsRepository {

    /** Persiste los ajustes iniciales del usuario (llamado al completar el onboarding). */
    suspend fun save(settings: UserSettings): Result<Unit>

    /** Actualiza todos los campos de los ajustes. */
    suspend fun update(settings: UserSettings): Result<Unit>

    /**
     * Obtiene los ajustes del usuario o `null` si no se han inicializado.
     */
    suspend fun getByUserId(userId: String): Result<UserSettings?>

    /** Flow reactivo que emite los ajustes actuales y sus cambios posteriores. */
    fun observe(userId: String): Flow<UserSettings?>
}
