package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.database.dao.GamificationDao
import com.eternamente.app.data.local.database.entity.toDomain
import com.eternamente.app.data.local.database.entity.toEntity
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.GamificationProfile
import com.eternamente.app.domain.repository.GamificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamificationRepositoryImpl @Inject constructor(
    private val gamificationDao: GamificationDao
) : GamificationRepository {

    override suspend fun getProfile(userId: String): Result<GamificationProfile> =
        withContext(Dispatchers.IO) {
            safeCall {
                gamificationDao.getByUserId(userId)?.toDomain()
                    ?: throw NoSuchElementException("Gamification profile not found: $userId")
            }
        }

    override fun observeProfile(userId: String): Flow<GamificationProfile?> =
        gamificationDao.observe(userId)
            .map { it?.toDomain() }
            .flowOn(Dispatchers.IO)

    override suspend fun addPoints(userId: String, pointsToAdd: Int): Result<GamificationProfile> =
        withContext(Dispatchers.IO) {
            safeCall {
                require(pointsToAdd >= 0) { "pointsToAdd must be non-negative" }
                gamificationDao.addPoints(userId, pointsToAdd)
                requireProfile(userId)
            }
        }

    override suspend fun updateStreak(
        userId: String,
        sessionDateIso: String
    ): Result<GamificationProfile> = withContext(Dispatchers.IO) {
        safeCall {
            val current  = requireProfile(userId)
            val newDate  = LocalDate.parse(sessionDateIso)
            val lastDate = current.lastSessionDate?.let { LocalDate.parse(it) }

            when {
                lastDate == null               -> gamificationDao.incrementStreak(userId, sessionDateIso)
                newDate == lastDate            -> { /* mismo día, no-op */ }
                newDate == lastDate.plusDays(1) -> gamificationDao.incrementStreak(userId, sessionDateIso)
                else                           -> gamificationDao.resetStreak(userId)
            }
            requireProfile(userId)
        }
    }

    override suspend fun unlockBadge(userId: String, badge: Badge): Result<GamificationProfile> =
        withContext(Dispatchers.IO) {
            safeCall {
                val current = requireProfile(userId)
                if (!current.hasBadge(badge)) {
                    gamificationDao.appendBadge(userId, badge.name)
                }
                requireProfile(userId)
            }
        }

    override suspend fun initializeProfile(userId: String): Result<GamificationProfile> =
        withContext(Dispatchers.IO) {
            safeCall {
                gamificationDao.getByUserId(userId)?.toDomain() ?: run {
                    val blank = GamificationProfile(
                        userId          = userId,
                        totalPoints     = 0,
                        currentStreak   = 0,
                        maxStreak       = 0,
                        lastSessionDate = null,
                        badges          = emptyList()
                    )
                    gamificationDao.insert(blank.toEntity())
                    blank
                }
            }
        }

    private suspend fun requireProfile(userId: String): GamificationProfile =
        gamificationDao.getByUserId(userId)?.toDomain()
            ?: throw NoSuchElementException("Gamification profile not found: $userId")
}
