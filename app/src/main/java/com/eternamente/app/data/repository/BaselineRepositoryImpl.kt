package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.database.dao.BaselineDao
import com.eternamente.app.data.local.database.entity.toDomain
import com.eternamente.app.data.local.database.entity.toEntity
import com.eternamente.app.domain.model.CognitiveBaseline
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.repository.BaselineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaselineRepositoryImpl @Inject constructor(
    private val baselineDao: BaselineDao
) : BaselineRepository {

    override suspend fun save(baseline: CognitiveBaseline): Result<Unit> =
        withContext(Dispatchers.IO) {
            safeCall { baselineDao.insert(baseline.toEntity()) }
        }

    override suspend fun update(baseline: CognitiveBaseline): Result<Unit> =
        withContext(Dispatchers.IO) {
            safeCall { baselineDao.update(baseline.toEntity()) }
        }

    override suspend fun getByUserId(userId: String): Result<CognitiveBaseline?> =
        withContext(Dispatchers.IO) {
            safeCall { baselineDao.getByUserId(userId)?.toDomain() }
        }

    override suspend fun updateByDomain(
        userId: String,
        domain: CognitiveDomain,
        score: Float
    ): Result<Unit> = withContext(Dispatchers.IO) {
        safeCall { baselineDao.updateByDomain(userId, domain.name, score) }
    }

    override fun observe(userId: String): Flow<CognitiveBaseline?> =
        baselineDao.observe(userId)
            .map { it?.toDomain() }
            .flowOn(Dispatchers.IO)
}
