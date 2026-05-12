package com.eternamente.app.domain.repository

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult
import kotlinx.coroutines.flow.Flow

/**
 * Contract for [GameResult] persistence and cognitive analytics queries.
 *
 * Game results are the primary data source for the ML prediction pipeline.
 * The data layer stores them in the encrypted Room database and computes
 * aggregates on-device to avoid sending raw scores to the cloud.
 */
interface GameResultRepository {

    /**
     * Persists a single [GameResult] produced at the end of a mini-game.
     *
     * @param result The [GameResult] to store.
     * @return [Result.Success] with the persisted entity, or [Result.Error] on failure.
     */
    suspend fun saveGameResult(result: GameResult): Result<GameResult>

    /**
     * Atomically persists a batch of [GameResult] entities within a single transaction.
     *
     * Prefer this over multiple [saveGameResult] calls when saving a session's
     * full set of results to ensure all-or-nothing persistence.
     *
     * @param results The list of [GameResult] entities to persist.
     * @return [Result.Success] with the stored list, or [Result.Error] on failure.
     */
    suspend fun saveGameResults(results: List<GameResult>): Result<List<GameResult>>

    /**
     * Emits all game results for a given session, ordered by insertion time ascending.
     *
     * @param sessionId UUID of the parent [com.eternamente.app.domain.model.CognitiveSession].
     */
    fun observeResultsForSession(sessionId: String): Flow<List<GameResult>>

    /**
     * Returns all game results for a user filtered by [CognitiveDomain].
     *
     * Used to build longitudinal trend charts for a specific cognitive domain.
     *
     * @param userId UUID of the target user.
     * @param domain The [CognitiveDomain] to filter by.
     * @return [Result.Success] with the matching results, or [Result.Error] on failure.
     */
    suspend fun getResultsByDomain(userId: String, domain: CognitiveDomain): Result<List<GameResult>>

    /**
     * Returns the [limit] most recent game results for a user across all sessions.
     *
     * Used as the sliding window input for the ML prediction model.
     *
     * @param userId UUID of the target user.
     * @param limit Maximum number of results to return.
     * @return [Result.Success] with the results (newest first), or [Result.Error] on failure.
     */
    suspend fun getLatestResults(userId: String, limit: Int): Result<List<GameResult>>

    /**
     * Computes the average [GameResult.scoreNormalized] per [CognitiveDomain]
     * over the last [windowSessions] completed sessions for a user.
     *
     * The computation is performed in-database (SQL AVG) to minimize memory pressure.
     *
     * @param userId UUID of the target user.
     * @param windowSessions Number of most recent sessions to include (defaults to 5).
     * @return [Result.Success] with a map of [CognitiveDomain] to average normalized score,
     *   or [Result.Error] on query failure.
     */
    suspend fun getAverageScoresByDomain(
        userId: String,
        windowSessions: Int = 5
    ): Result<Map<CognitiveDomain, Float>>

    /**
     * Returns the score trend (list of [GameResult.scoreNormalized] values ordered by date)
     * for a specific [gameId] across the user's session history.
     *
     * Used to render per-game longitudinal trend sparklines.
     *
     * @param userId UUID of the target user.
     * @param gameId Stable identifier of the mini-game to query.
     * @param limit Maximum number of historical data points to return.
     */
    suspend fun getScoreTrendForGame(
        userId: String,
        gameId: String,
        limit: Int = 20
    ): Result<List<Float>>
}
