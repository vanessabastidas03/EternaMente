package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.core.getOrNull
import com.eternamente.app.core.safeCall
import com.eternamente.app.domain.ml.CognitiveAnalyzer
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.MlRepository
import java.util.UUID

/**
 * Triggers on-device cognitive risk analysis and returns the resulting [MlPrediction].
 *
 * Execution pipeline:
 * 1. Verifies that at least [MIN_SESSIONS_REQUIRED] game results exist for the user.
 * 2. Delegates to [CognitiveAnalyzer.analyze] which runs feature extraction,
 *    Isolation Forest anomaly detection, and TFLite (or statistical fallback) inference.
 * 3. Persists the result via [MlRepository.savePrediction] and returns it.
 */
class AnalyzeCognitivePatternUseCase(
    private val cognitiveAnalyzer:    CognitiveAnalyzer,
    private val gameResultRepository: GameResultRepository,
    private val mlRepository:         MlRepository,
    private val sessionRepository:    com.eternamente.app.domain.repository.SessionRepository
) {

    /**
     * @param userId            UUID of the user to evaluate.
     * @param sessionWindowSize Unused at this layer; [CognitiveAnalyzer] controls the window.
     * @return [Result.Success] with the persisted [MlPrediction], or [Result.Error] on failure.
     */
    suspend operator fun invoke(
        userId: String,
        @Suppress("UNUSED_PARAMETER") sessionWindowSize: Int = DEFAULT_WINDOW
    ): Result<MlPrediction> = safeCall {
        val sessionCount = sessionRepository.countAllCompletedSessions(userId).getOrNull() ?: 0
        if (sessionCount < MIN_SESSIONS_REQUIRED) {
            throw IllegalStateException(
                "Se necesitan al menos $MIN_SESSIONS_REQUIRED sesiones para el análisis " +
                "(tienes $sessionCount)"
            )
        }

        val analysis = cognitiveAnalyzer.analyze(userId)

        val prediction = MlPrediction(
            id             = UUID.randomUUID().toString(),
            userId         = userId,
            predictionDate = analysis.analyzedAt,
            riskScore      = analysis.combinedRiskScore,
            alertLevel     = analysis.alertLevel,
            domainsFlagged = analysis.flaggedDomains,
            explanation    = analysis.explanation
        )

        mlRepository.savePrediction(prediction).let { result ->
            when (result) {
                is Result.Success -> result.data
                is Result.Error   -> throw result.exception
            }
        }
    }

    companion object {
        const val MIN_SESSIONS_REQUIRED = 3
        const val DEFAULT_WINDOW        = 5
    }
}
