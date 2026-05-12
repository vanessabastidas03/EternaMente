package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.CognitiveBaseline
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.repository.UserRepository

/**
 * Aggregates all data required to render the user's cognitive progress report.
 *
 * The report is a snapshot compiled from multiple sources:
 * - [UserRepository] for the user's demographic profile.
 * - [GameResultRepository] for the last [RECENT_RESULTS_LIMIT] game results and
 *   per-domain average scores.
 * - [MlRepository] for the most recent ML risk prediction.
 *
 * The [CognitiveBaseline] is computed from the initial BASELINE session results
 * stored in [GameResultRepository]; it is not fetched from a separate repository.
 *
 * @property userRepository Provides the [User] profile.
 * @property gameResultRepository Provides aggregated game result statistics.
 * @property mlRepository Provides the latest [MlPrediction].
 */
class GenerateReportUseCase(
    private val userRepository: UserRepository,
    private val gameResultRepository: GameResultRepository,
    private val mlRepository: MlRepository
) {

    /**
     * Compiles the full cognitive report for a user.
     *
     * All sub-queries run sequentially to avoid overwhelming the SQLite connection.
     * Consider calling from [kotlinx.coroutines.Dispatchers.IO].
     *
     * @param userId UUID of the user for whom the report is generated.
     * @return [Result.Success] with a [CognitiveReport], or [Result.Error] if
     *   the user is not found or any sub-query fails.
     */
    suspend operator fun invoke(userId: String): Result<CognitiveReport> {
        TODO("Not yet implemented")
    }

    /**
     * Aggregated, immutable snapshot of a user's cognitive status.
     *
     * Designed to be a pure data transfer object — no business logic should live here.
     * All presentation formatting is delegated to the UI layer.
     *
     * @property user Full [User] profile including demographic fields.
     * @property recentResults The [RECENT_RESULTS_LIMIT] most recent [GameResult] records,
     *   ordered newest first. Used for trend charts and per-game breakdowns.
     * @property baseline Reference [CognitiveBaseline] from the initial assessment;
     *   `null` if the baseline session has not yet been completed.
     * @property latestPrediction Most recent on-device ML risk prediction;
     *   `null` if no prediction has been generated yet.
     * @property generatedAt Epoch-millisecond timestamp when this snapshot was compiled.
     */
    data class CognitiveReport(
        val user: User,
        val recentResults: List<GameResult>,
        val baseline: CognitiveBaseline?,
        val latestPrediction: MlPrediction?,
        val generatedAt: Long
    )

    companion object {
        /** Number of recent game results to include in the report. */
        const val RECENT_RESULTS_LIMIT = 50
    }
}
