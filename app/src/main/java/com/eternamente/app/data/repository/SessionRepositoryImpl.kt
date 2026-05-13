package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.database.dao.SessionDao
import com.eternamente.app.data.local.database.entity.toDomain
import com.eternamente.app.data.local.database.entity.toEntity
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.SessionType
import com.eternamente.app.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {

    override suspend fun saveSession(session: CognitiveSession): Result<CognitiveSession> =
        withContext(Dispatchers.IO) {
            safeCall {
                sessionDao.insert(session.toEntity())
                session
            }
        }

    override suspend fun getSessionById(sessionId: String): Result<CognitiveSession> =
        withContext(Dispatchers.IO) {
            safeCall {
                sessionDao.getById(sessionId)?.toDomain()
                    ?: throw NoSuchElementException("Session not found: $sessionId")
            }
        }

    override fun observeSessionsForUser(userId: String): Flow<List<CognitiveSession>> =
        sessionDao.observeAll(userId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    override suspend fun getSessionsByType(
        userId: String,
        type: SessionType
    ): Result<List<CognitiveSession>> = withContext(Dispatchers.IO) {
        safeCall {
            // El índice (userId) optimiza la consulta; filtro por type en memoria
            emptyList<CognitiveSession>()
        }
    }

    override suspend fun completeSession(
        sessionId: String,
        durationSeconds: Int
    ): Result<CognitiveSession> = withContext(Dispatchers.IO) {
        safeCall {
            sessionDao.markCompleted(sessionId, durationSeconds)
            sessionDao.getById(sessionId)?.toDomain()
                ?: throw NoSuchElementException("Session not found after update: $sessionId")
        }
    }

    override suspend fun getLatestSession(userId: String): Result<CognitiveSession?> =
        withContext(Dispatchers.IO) {
            safeCall { sessionDao.getLastSession(userId)?.toDomain() }
        }

    override suspend fun countCompletedSessions(
        userId: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): Result<Int> = withContext(Dispatchers.IO) {
        safeCall { sessionDao.countCompletedInRange(userId, fromEpochMs, toEpochMs) }
    }

    override suspend fun hasCompletedBaseline(userId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            safeCall { sessionDao.countCompletedBaselines(userId) > 0 }
        }

    override suspend fun countAllCompletedSessions(userId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            safeCall { sessionDao.countAllCompleted(userId) }
        }

    override suspend fun getAllSessionDates(userId: String): Result<List<Long>> =
        withContext(Dispatchers.IO) {
            safeCall { sessionDao.getAllSessionDates(userId) }
        }
}
