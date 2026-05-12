package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.MlRepository

/**
 * Triggers on-device cognitive risk analysis and returns the resulting [MlPrediction].
 *
 * Execution pipeline:
 * 1. Verifies that at least [MIN_SESSIONS_REQUIRED] completed sessions exist.
 * 2. Fetches the [sessionWindowSize] most recent game results as ML feature input.
 * 3. Delegates to [MlRepository.runPrediction] which runs the TFLite model.
 * 4. Returns the persisted [MlPrediction].
 *
 * This use case should be triggered after every [com.eternamente.app.domain.model.SessionType.WEEKLY_FULL]
 * session and optionally after [com.eternamente.app.domain.model.SessionType.DAILY] sessions
 * once sufficient history has accumulated.
 *
 * @property gameResultRepository Used to verify data sufficiency before running inference.
 * @property mlRepository Handles TFLite inference execution and prediction persistence.
 */
class AnalyzeCognitivePatternUseCase(
    private val gameResultRepository: GameResultRepository,
    private val mlRepository: MlRepository
) {

    /**
     * Executes the cognitive analysis pipeline.
     *
     * @param userId UUID of the user to evaluate.
     * @param sessionWindowSize Number of recent sessions whose results are included
     *   in the ML feature vector. Defaults to [DEFAULT_WINDOW].
     * @return [Result.Success] with the new [MlPrediction], or [Result.Error] if:
     *   - Fewer than [MIN_SESSIONS_REQUIRED] completed sessions exist.
     *   - The TFLite model fails to load or run.
     *   - The result cannot be persisted.
     */
    suspend operator fun invoke(
        userId: String,
        sessionWindowSize: Int = DEFAULT_WINDOW
    ): Result<MlPrediction> {
        TODO("Not yet implemented")
    }

    companion object {
        /** Minimum number of completed sessions required before inference is permitted. */
        const val MIN_SESSIONS_REQUIRED = 3

        /** Default sliding window for the ML feature vector. */
        const val DEFAULT_WINDOW = 5
    }
}
