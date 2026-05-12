package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.db.dao.SessionDao
import com.eternamente.app.data.local.db.entity.toDomain
import com.eternamente.app.data.local.db.entity.toEntity
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.SessionType
import com.eternamente.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [SessionRepository].
 *
 * All data lives in the encrypted Room database via [SessionDao].
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {

    override suspend fun saveSession(session: CognitiveSession): Result<CognitiveSession> = safeCall {
        sessionDao.insertSession(session.toEntity())
        session
    }

    override suspend fun getSessionById(sessionId: String): Result<CognitiveSession> = safeCall {
        sessionDao.getSessionById(sessionId)?.toDomain()
            ?: throw NoSuchElementException("Session not found: $sessionId")
    }

    override fun observeSessionsForUser(userId: String): Flow<List<CognitiveSession>> =
        sessionDao.observeSessionsForUser(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun getSessionsByType(
        userId: String,
        type: SessionType
    ): Result<List<CognitiveSession>> = safeCall {
        sessionDao.getSessionsByType(userId, type.name).map { it.toDomain() }
    }

    override suspend fun completeSession(
        sessionId: String,
        durationSeconds: Int
    ): Result<CognitiveSession> = safeCall {
        sessionDao.markCompleted(sessionId, durationSeconds)
        sessionDao.getSessionById(sessionId)?.toDomain()
            ?: throw NoSuchElementException("Session not found after update: $sessionId")
    }

    override suspend fun getLatestSession(userId: String): Result<CognitiveSession?> = safeCall {
        sessionDao.getLatestSession(userId)?.toDomain()
    }

    override suspend fun countCompletedSessions(
        userId: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): Result<Int> = safeCall {
        sessionDao.countCompletedSessions(userId, fromEpochMs, toEpochMs)
    }

    override suspend fun hasCompletedBaseline(userId: String): Result<Boolean> = safeCall {
        sessionDao.countCompletedBaselines(userId) > 0
    }
}
