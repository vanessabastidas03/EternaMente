package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.db.dao.GameResultDao
import com.eternamente.app.data.local.db.dao.MlPredictionDao
import com.eternamente.app.data.local.db.entity.toDomain
import com.eternamente.app.data.local.db.entity.toEntity
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.repository.MlRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [MlRepository].
 *
 * **Inference strategy (Phase 1 — heuristic)**:
 * Until the TFLite model file (`assets/mci_risk_v1.tflite`) is integrated,
 * risk is estimated from the weighted average of normalised scores across all
 * cognitive domains. Domains with a score below [LOW_SCORE_THRESHOLD] are flagged.
 * Replace [runHeuristicPrediction] with actual TFLite inference in Phase 2.
 *
 * Persistence: [MlPredictionDao] backed by the encrypted Room database.
 */
@Singleton
class MlRepositoryImpl @Inject constructor(
    private val mlPredictionDao: MlPredictionDao,
    private val gameResultDao: GameResultDao
) : MlRepository {

    private companion object {
        const val MODEL_VERSION       = "heuristic_v1"
        const val MIN_RESULTS         = 5       // Minimum game results required
        const val RESULT_FETCH_LIMIT  = 60      // Rolling window for feature computation
        const val LOW_SCORE_THRESHOLD = 40f     // Domains below this are flagged
    }

    override suspend fun runPrediction(userId: String): Result<MlPrediction> = safeCall {
        val recent = gameResultDao.getLatestResults(userId, RESULT_FETCH_LIMIT)
        if (recent.size < MIN_RESULTS) {
            throw IllegalStateException(
                "Insufficient data: need $MIN_RESULTS results, found ${recent.size}"
            )
        }
        val prediction = runHeuristicPrediction(userId, recent.map { it.toDomain() })
        mlPredictionDao.insertPrediction(prediction.toEntity())
        prediction
    }

    override suspend fun getLatestPrediction(userId: String): Result<MlPrediction?> = safeCall {
        mlPredictionDao.getLatestPrediction(userId)?.toDomain()
    }

    override fun observePredictionHistory(userId: String): Flow<List<MlPrediction>> =
        mlPredictionDao.observePredictionHistory(userId)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun savePrediction(prediction: MlPrediction): Result<MlPrediction> = safeCall {
        mlPredictionDao.insertPrediction(prediction.toEntity())
        prediction
    }

    override suspend fun purgePredictionsBefore(userId: String, beforeEpochMs: Long): Result<Int> = safeCall {
        mlPredictionDao.deletePredictionsBefore(userId, beforeEpochMs)
    }

    override suspend fun getCurrentModelVersion(): Result<String> = safeCall { MODEL_VERSION }

    // ── Heuristic inference (Phase 1) ────────────────────────────────────────

    private fun runHeuristicPrediction(
        userId: String,
        results: List<com.eternamente.app.domain.model.GameResult>
    ): MlPrediction {
        val domainAverages: Map<CognitiveDomain, Float> = results
            .groupBy { it.domain }
            .mapValues { (_, group) -> group.map { it.scoreNormalized }.average().toFloat() }

        val overallAvg   = domainAverages.values.average().toFloat()
        val riskScore    = ((100f - overallAvg) / 100f).coerceIn(0f, 1f)
        val alertLevel   = when {
            riskScore < 0.30f -> AlertLevel.NORMAL
            riskScore < 0.60f -> AlertLevel.WATCH
            else              -> AlertLevel.ALERT
        }
        val flagged = domainAverages
            .filter { (_, avg) -> avg < LOW_SCORE_THRESHOLD }
            .keys
            .toList()

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
            AlertLevel.NORMAL ->
                "Tu rendimiento cognitivo se mantiene dentro de rangos esperados para tu grupo de edad."
            AlertLevel.WATCH  -> {
                val domains = flagged.joinToString(", ") { it.name.lowercase() }
                "Se observa una tendencia descendente en: $domains. Se recomienda mantener la frecuencia de sesiones."
            }
            AlertLevel.ALERT  -> {
                val domains = flagged.joinToString(", ") { it.name.lowercase() }
                "Cambios significativos detectados en: $domains. Se recomienda consultar con un profesional de salud."
            }
        }
}
