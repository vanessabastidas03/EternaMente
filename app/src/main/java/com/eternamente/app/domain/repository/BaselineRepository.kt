package com.eternamente.app.domain.repository

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.CognitiveBaseline
import com.eternamente.app.domain.model.CognitiveDomain
import kotlinx.coroutines.flow.Flow

/**
 * Contrato para la gestión del perfil cognitivo de referencia ([CognitiveBaseline]).
 *
 * El baseline se establece tras la sesión inicial de tipo BASELINE y sirve como
 * punto de comparación para medir el progreso longitudinal.
 */
interface BaselineRepository {

    /** Persiste o reemplaza el baseline completo del usuario. */
    suspend fun save(baseline: CognitiveBaseline): Result<Unit>

    /** Actualiza todos los campos del baseline. */
    suspend fun update(baseline: CognitiveBaseline): Result<Unit>

    /**
     * Obtiene el baseline del usuario o `null` si aún no se ha establecido.
     */
    suspend fun getByUserId(userId: String): Result<CognitiveBaseline?>

    /**
     * Actualiza la puntuación de un único dominio cognitivo y recalcula [CognitiveBaseline.overallScore].
     *
     * @param userId UUID del usuario.
     * @param domain Dominio cognitivo a actualizar.
     * @param score  Nueva puntuación normalizada (0–100).
     */
    suspend fun updateByDomain(userId: String, domain: CognitiveDomain, score: Float): Result<Unit>

    /** Flow reactivo que emite el baseline actual y sus actualizaciones posteriores. */
    fun observe(userId: String): Flow<CognitiveBaseline?>
}
