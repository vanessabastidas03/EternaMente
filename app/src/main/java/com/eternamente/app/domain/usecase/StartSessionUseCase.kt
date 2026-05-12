package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.SessionType
import com.eternamente.app.domain.repository.SessionRepository

/**
 * Creates and persists a new [CognitiveSession] after validating business rules.
 *
 * Domain rules enforced before creating the session:
 * - A [SessionType.BASELINE] session can only be started once per user.
 * - A [SessionType.DAILY] or [SessionType.WEEKLY_FULL] session requires a
 *   completed BASELINE to already exist.
 * - At most one incomplete session may exist at a time (concurrency guard).
 *
 * @property sessionRepository Handles session persistence and prerequisite queries.
 */
class StartSessionUseCase(
    private val sessionRepository: SessionRepository
) {

    /**
     * Validates preconditions and creates a new [CognitiveSession].
     *
     * @param userId UUID of the user starting the session.
     * @param type The [SessionType] for the new session.
     * @param startTimestamp Epoch-millisecond timestamp of session initiation.
     *   Callers should pass the wall-clock time at the moment the user taps "Start".
     * @return [Result.Success] with the newly created [CognitiveSession], or
     *   [Result.Error] if a business rule is violated or the insert fails.
     */
    suspend operator fun invoke(
        userId: String,
        type: SessionType,
        startTimestamp: Long
    ): Result<CognitiveSession> {
        TODO("Not yet implemented")
    }
}
