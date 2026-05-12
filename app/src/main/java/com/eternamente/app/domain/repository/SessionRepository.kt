package com.eternamente.app.domain.repository

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.SessionType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for [CognitiveSession] persistence, querying, and lifecycle management.
 *
 * The data layer implementation stores sessions in the encrypted Room database
 * and optionally syncs completed sessions to Firestore.
 */
interface SessionRepository {

    /**
     * Persists a new [CognitiveSession] record.
     *
     * Called by [com.eternamente.app.domain.usecase.StartSessionUseCase] when the user
     * begins a session.
     *
     * @param session The [CognitiveSession] to store. [CognitiveSession.completed]
     *   should be `false` at this point.
     * @return [Result.Success] with the stored session, or [Result.Error] on failure.
     */
    suspend fun saveSession(session: CognitiveSession): Result<CognitiveSession>

    /**
     * Retrieves a [CognitiveSession] by its unique identifier.
     *
     * @param sessionId UUID of the target session.
     * @return [Result.Success] with the session, or [Result.Error] if not found.
     */
    suspend fun getSessionById(sessionId: String): Result<CognitiveSession>

    /**
     * Emits all sessions for a user ordered by [CognitiveSession.sessionDate] descending.
     *
     * The Flow updates reactively whenever new sessions are inserted or existing
     * sessions are updated (e.g., marked as completed).
     *
     * @param userId UUID of the target user.
     */
    fun observeSessionsForUser(userId: String): Flow<List<CognitiveSession>>

    /**
     * Returns all sessions of a specific [SessionType] for a user.
     *
     * @param userId UUID of the target user.
     * @param type The [SessionType] filter to apply.
     * @return [Result.Success] with the matching sessions, or [Result.Error] on failure.
     */
    suspend fun getSessionsByType(userId: String, type: SessionType): Result<List<CognitiveSession>>

    /**
     * Marks a session as completed and sets its final duration.
     *
     * Called when the user submits the last game result of the session.
     *
     * @param sessionId UUID of the session to update.
     * @param durationSeconds Total elapsed time from session start to end.
     * @return [Result.Success] with the updated [CognitiveSession], or [Result.Error] on failure.
     */
    suspend fun completeSession(sessionId: String, durationSeconds: Int): Result<CognitiveSession>

    /**
     * Retrieves the most recent [CognitiveSession] for a user regardless of type.
     *
     * Used to determine streak continuity and suggest the next session type.
     *
     * @param userId UUID of the target user.
     * @return [Result.Success] with the latest session or `null` if none exist.
     */
    suspend fun getLatestSession(userId: String): Result<CognitiveSession?>

    /**
     * Returns the count of completed sessions within a half-open date range.
     *
     * Used to compute weekly adherence metrics and trigger badge evaluation.
     *
     * @param userId UUID of the target user.
     * @param fromEpochMs Start of range in epoch milliseconds (inclusive).
     * @param toEpochMs End of range in epoch milliseconds (inclusive).
     * @return [Result.Success] with the count, or [Result.Error] on query failure.
     */
    suspend fun countCompletedSessions(
        userId: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): Result<Int>

    /**
     * Returns `true` if the user has at least one completed [SessionType.BASELINE] session.
     *
     * The baseline must exist before [SessionType.DAILY] sessions are permitted.
     *
     * @param userId UUID of the target user.
     */
    suspend fun hasCompletedBaseline(userId: String): Result<Boolean>
}
