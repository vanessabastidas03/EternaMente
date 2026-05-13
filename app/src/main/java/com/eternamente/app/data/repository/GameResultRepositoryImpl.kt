package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.database.dao.GameResultDao
import com.eternamente.app.data.local.database.dao.SessionDao
import com.eternamente.app.data.local.database.entity.toDomain
import com.eternamente.app.data.local.database.entity.toEntity
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.repository.GameResultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameResultRepositoryImpl @Inject constructor(
    private val gameResultDao: GameResultDao,
    private val sessionDao: SessionDao    // Para resolver userId a partir de sessionId
) : GameResultRepository {

    override suspend fun saveGameResult(result: GameResult): Result<GameResult> =
        withContext(Dispatchers.IO) {
            safeCall {
                // Obtener userId real desde la sesión — evita pasar userId por toda la cadena
                val userId = sessionDao.getById(result.sessionId)?.userId ?: ""
                gameResultDao.insert(result.toEntity(userId))
                result
            }
        }

    override suspend fun saveGameResults(results: List<GameResult>): Result<List<GameResult>> =
        withContext(Dispatchers.IO) {
            safeCall {
                // Cachear userId para no hacer N queries al mismo sessionId
                val sessionUserCache = mutableMapOf<String, String>()
                gameResultDao.insertAll(results.map { r ->
                    val userId = sessionUserCache.getOrPut(r.sessionId) {
                        sessionDao.getById(r.sessionId)?.userId ?: ""
                    }
                    r.toEntity(userId)
                })
                results
            }
        }

    override fun observeResultsForSession(sessionId: String): Flow<List<GameResult>> =
        gameResultDao.getResultsBySession(sessionId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    override suspend fun getResultsByDomain(
        userId: String,
        domain: CognitiveDomain
    ): Result<List<GameResult>> = withContext(Dispatchers.IO) {
        safeCall {
            gameResultDao.getResultsByDomain(userId, domain.name, Int.MAX_VALUE)
                .map { it.toDomain() }
        }
    }

    override suspend fun getLatestResults(userId: String, limit: Int): Result<List<GameResult>> =
        withContext(Dispatchers.IO) {
            safeCall { gameResultDao.getLatestResults(userId, limit).map { it.toDomain() } }
        }

    override suspend fun getAverageScoresByDomain(
        userId: String,
        windowSessions: Int
    ): Result<Map<CognitiveDomain, Float>> = withContext(Dispatchers.IO) {
        safeCall {
            val fromMs = System.currentTimeMillis() - windowSessions * 7L * 24 * 3_600_000
            gameResultDao.getAveragesByDomainSince(userId, fromMs)
                .associate { row -> CognitiveDomain.valueOf(row.domain) to row.avgScore }
        }
    }

    override suspend fun getScoreTrendForGame(
        userId: String,
        gameId: String,
        limit: Int
    ): Result<List<Float>> = withContext(Dispatchers.IO) {
        safeCall { gameResultDao.getScoreTrend(userId, gameId, limit) }
    }

    override suspend fun countGameResultsForUserToday(
        userId: String,
        fromEpochMs: Long
    ): Result<Int> = withContext(Dispatchers.IO) {
        safeCall { gameResultDao.countGameResultsForUserToday(userId, fromEpochMs) }
    }

    /** Versión extendida: promedio por dominio desde hace N semanas (para gráficos). */
    suspend fun getWeeklyAverages(userId: String, weeksBack: Int): Result<Map<CognitiveDomain, Float>> =
        withContext(Dispatchers.IO) {
            safeCall {
                val fromMs = System.currentTimeMillis() - weeksBack * 7L * 24 * 3_600_000
                gameResultDao.getAveragesByDomainSince(userId, fromMs)
                    .associate { row -> CognitiveDomain.valueOf(row.domain) to row.avgScore }
            }
        }
}
