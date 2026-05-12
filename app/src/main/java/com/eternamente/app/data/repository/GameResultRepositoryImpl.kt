package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.db.dao.GameResultDao
import com.eternamente.app.data.local.db.entity.toDomain
import com.eternamente.app.data.local.db.entity.toEntity
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.repository.GameResultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [GameResultRepository].
 *
 * All data lives in the encrypted Room database via [GameResultDao].
 */
@Singleton
class GameResultRepositoryImpl @Inject constructor(
    private val gameResultDao: GameResultDao
) : GameResultRepository {

    override suspend fun saveGameResult(result: GameResult): Result<GameResult> = safeCall {
        gameResultDao.insertResult(result.toEntity())
        result
    }

    override suspend fun saveGameResults(results: List<GameResult>): Result<List<GameResult>> = safeCall {
        gameResultDao.insertResults(results.map { it.toEntity() })
        results
    }

    override fun observeResultsForSession(sessionId: String): Flow<List<GameResult>> =
        gameResultDao.observeResultsForSession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun getResultsByDomain(
        userId: String,
        domain: CognitiveDomain
    ): Result<List<GameResult>> = safeCall {
        gameResultDao.getResultsByDomain(userId, domain.name).map { it.toDomain() }
    }

    override suspend fun getLatestResults(userId: String, limit: Int): Result<List<GameResult>> = safeCall {
        gameResultDao.getLatestResults(userId, limit).map { it.toDomain() }
    }

    override suspend fun getAverageScoresByDomain(
        userId: String,
        windowSessions: Int
    ): Result<Map<CognitiveDomain, Float>> = safeCall {
        gameResultDao.getAverageScoresByDomain(userId, windowSessions)
            .associate { row -> CognitiveDomain.valueOf(row.domain) to row.avgScore }
    }

    override suspend fun getScoreTrendForGame(
        userId: String,
        gameId: String,
        limit: Int
    ): Result<List<Float>> = safeCall {
        gameResultDao.getScoreTrendForGame(userId, gameId, limit)
    }
}
