package com.eternamente.app.data.repository

import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.database.dao.GameResultDao
import com.eternamente.app.data.local.database.dao.SessionDao
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.repository.FeatureQueryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureQueryRepositoryImpl @Inject constructor(
    private val gameResultDao: GameResultDao,
    private val sessionDao: SessionDao
) : FeatureQueryRepository {

    override suspend fun avgRtByDomain(
        userId: String, domain: CognitiveDomain, fromMs: Long
    ): Float? = withContext(Dispatchers.IO) {
        gameResultDao.avgRtByDomainSince(userId, domain.name, fromMs)
    }

    override suspend fun avgAccuracyByDomain(
        userId: String, domain: CognitiveDomain, fromMs: Long
    ): Float? = withContext(Dispatchers.IO) {
        gameResultDao.avgAccuracyByDomainSince(userId, domain.name, fromMs)
    }

    override suspend fun accuracySeriesByDomain(
        userId: String, domain: CognitiveDomain, fromMs: Long
    ): List<Float> = withContext(Dispatchers.IO) {
        gameResultDao.accuracySeriesByDomainSince(userId, domain.name, fromMs)
    }

    override suspend fun allRtSince(userId: String, fromMs: Long): List<Float> =
        withContext(Dispatchers.IO) {
            gameResultDao.allRtSince(userId, fromMs)
        }

    override suspend fun countByDomainSince(
        userId: String, domain: CognitiveDomain, fromMs: Long
    ): Int = withContext(Dispatchers.IO) {
        gameResultDao.countByDomainSince(userId, domain.name, fromMs)
    }

    override suspend fun earliestScores(userId: String, limit: Int): List<Float> =
        withContext(Dispatchers.IO) {
            gameResultDao.earliestScores(userId, limit)
        }

    override suspend fun countAllSessionsInRange(
        userId: String, fromMs: Long, toMs: Long
    ): Int = withContext(Dispatchers.IO) {
        sessionDao.countAllSessionsInRange(userId, fromMs, toMs)
    }

    override suspend fun countCompletedSessionsInRange(
        userId: String, fromMs: Long, toMs: Long
    ): Int = withContext(Dispatchers.IO) {
        sessionDao.countCompletedInRange(userId, fromMs, toMs)
    }
}
