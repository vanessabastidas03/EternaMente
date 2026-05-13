package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.database.dao.GameResultDao
import com.eternamente.app.data.local.database.dao.MlPredictionDao
import com.eternamente.app.data.local.database.entity.toDomain
import com.eternamente.app.data.local.database.entity.toEntity
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.repository.MlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [MlRepository] respaldada por Room + SQLCipher.
 *
 * **Estrategia de inferencia Phase 1 — heurística:**
 * Hasta integrar el modelo TFLite, el riesgo se estima desde el promedio ponderado
 * de puntuaciones normalizadas por dominio. Sustituir [runHeuristicPrediction] con
 * inferencia TFLite real en Phase 2.
 *
 * Usa DAOs del paquete `data.local.database` (provisto por [com.eternamente.app.di.DatabaseModule]).
 */
@Singleton
class MlRepositoryImpl @Inject constructor(
    private val mlPredictionDao: MlPredictionDao,   // data.local.database.dao
    private val gameResultDao: GameResultDao         // data.local.database.dao
) : MlRepository {

    private companion object {
        const val MODEL_VERSION      = "heuristic_v1"
        const val MIN_RESULTS        = 5
        const val RESULT_FETCH_LIMIT = 60
        const val LOW_SCORE_THRESHOLD = 40f
    }

    override suspend fun runPrediction(userId: String): Result<MlPrediction> =
        withContext(Dispatchers.IO) {
            safeCall {
                val recent = gameResultDao.getLatestResults(userId, RESULT_FETCH_LIMIT)
                if (recent.size < MIN_RESULTS) {
                    throw IllegalStateException(
                        "Datos insuficientes: se necesitan $MIN_RESULTS resultados, encontrados ${recent.size}"
                    )
                }
                val prediction = runHeuristicPrediction(userId, recent.map { it.toDomain() })
                mlPredictionDao.insert(prediction.toEntity())    // insert() en nuevo DAO
                prediction
            }
        }

    override suspend fun getLatestPrediction(userId: String): Result<MlPrediction?> =
        withContext(Dispatchers.IO) {
            safeCall { mlPredictionDao.getLatestForUser(userId)?.toDomain() }  // getLatestForUser()
        }

    override fun observePredictionHistory(userId: String): Flow<List<MlPrediction>> =
        mlPredictionDao.observeHistory(userId)       // observeHistory() en nuevo DAO
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    override suspend fun savePrediction(prediction: MlPrediction): Result<MlPrediction> =
        withContext(Dispatchers.IO) {
            safeCall {
                mlPredictionDao.insert(prediction.toEntity())
                prediction
            }
        }

    override suspend fun purgePredictionsBefore(userId: String, beforeEpochMs: Long): Result<Int> =
        withContext(Dispatchers.IO) {
            safeCall { mlPredictionDao.deleteOlderThan(userId, beforeEpochMs) }  // deleteOlderThan()
        }

    override suspend fun getCurrentModelVersion(): Result<String> = safeCall { MODEL_VERSION }

    // ── Inferencia heurística (Phase 1) ──────────────────────────────────────

    private fun runHeuristicPrediction(
        userId: String,
        results: List<GameResult>
    ): MlPrediction {
        val domainAverages: Map<CognitiveDomain, Float> = results
            .groupBy { it.domain }
            .mapValues { (_, group) -> group.map { it.scoreNormalized }.average().toFloat() }

        val overallAvg = domainAverages.values.average().toFloat()
        val riskScore  = ((100f - overallAvg) / 100f).coerceIn(0f, 1f)
        val alertLevel = when {
            riskScore < 0.30f -> AlertLevel.NORMAL
            riskScore < 0.60f -> AlertLevel.WATCH
            else              -> AlertLevel.ALERT
        }
        val flagged = domainAverages
            .filter { (_, avg) -> avg < LOW_SCORE_THRESHOLD }
            .keys.toList()

        return MlPrediction(
            id             = UUID.randomUUID().toString(),
            userId         = userId,
            predictionDate = System.currentTimeMillis(),
            riskScore      = riskScore,
            alertLevel     = alertLevel,
            domainsFlagged = flagged,
            explanation    = buildExplanation(alertLevel, flagged)
        )
    }

    private fun buildExplanation(alertLevel: AlertLevel, flagged: List<CognitiveDomain>): String =
        when (alertLevel) {
            AlertLevel.NORMAL -> "Tu rendimiento cognitivo se mantiene dentro de rangos esperados."
            AlertLevel.WATCH  -> {
                val domains = flagged.joinToString(", ") { it.name.lowercase() }
                "Se observa una tendencia descendente en: $domains. Se recomienda mantener la frecuencia."
            }
            AlertLevel.ALERT  -> {
                val domains = flagged.joinToString(", ") { it.name.lowercase() }
                "Cambios significativos detectados en: $domains. Consulte con un profesional de salud."
            }
        }
}
