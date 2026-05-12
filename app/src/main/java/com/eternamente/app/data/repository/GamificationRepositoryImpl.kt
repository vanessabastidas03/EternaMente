package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.db.dao.GamificationDao
import com.eternamente.app.data.local.db.entity.toDomain
import com.eternamente.app.data.local.db.entity.toEntity
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.GamificationProfile
import com.eternamente.app.domain.repository.GamificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [GamificationRepository].
 *
 * Streak logic:
 * - A streak increments when [sessionDateIso] is exactly one calendar day after
 *   [GamificationProfile.lastSessionDate].
 * - Any larger gap resets [GamificationProfile.currentStreak] to 1.
 * - Same-day sessions do not increment the streak (idempotent).
 *
 * [java.time.LocalDate] requires minSdk 26; EternaMente targets minSdk 29. ✓
 */
@Singleton
class GamificationRepositoryImpl @Inject constructor(
    private val gamificationDao: GamificationDao
) : GamificationRepository {

    override suspend fun getProfile(userId: String): Result<GamificationProfile> = safeCall {
        gamificationDao.getProfile(userId)?.toDomain()
            ?: throw NoSuchElementException("Gamification profile not found for user: $userId")
    }

    override fun observeProfile(userId: String): Flow<GamificationProfile?> =
        gamificationDao.observeProfile(userId).map { it?.toDomain() }

    override suspend fun addPoints(userId: String, pointsToAdd: Int): Result<GamificationProfile> = safeCall {
        require(pointsToAdd >= 0) { "pointsToAdd must be non-negative, was $pointsToAdd" }
        val current = requireProfile(userId)
        val updated = current.withAddedPoints(pointsToAdd)
        gamificationDao.updateProfile(updated.toEntity())
        updated
    }

    override suspend fun updateStreak(
        userId: String,
        sessionDateIso: String
    ): Result<GamificationProfile> = safeCall {
        val current     = requireProfile(userId)
        val newDate     = LocalDate.parse(sessionDateIso)
        val lastDate    = current.lastSessionDate?.let { LocalDate.parse(it) }

        val newStreak = when {
            lastDate == null              -> 1
            newDate == lastDate           -> current.currentStreak   // same-day: no-op
            newDate == lastDate.plusDays(1) -> current.currentStreak + 1
            else                          -> 1                       // gap: reset
        }

        val updated = current.copy(
            currentStreak   = newStreak,
            maxStreak       = maxOf(current.maxStreak, newStreak),
            lastSessionDate = sessionDateIso
        )
        gamificationDao.updateProfile(updated.toEntity())
        updated
    }

    override suspend fun unlockBadge(userId: String, badge: Badge): Result<GamificationProfile> = safeCall {
        val current = requireProfile(userId)
        if (current.hasBadge(badge)) return@safeCall current   // Already earned — idempotent

        val updated = current.copy(badges = current.badges + badge)
        gamificationDao.updateProfile(updated.toEntity())
        updated
    }

    override suspend fun initializeProfile(userId: String): Result<GamificationProfile> = safeCall {
        // No-op if a profile already exists
        gamificationDao.getProfile(userId)?.toDomain() ?: run {
            val blank = GamificationProfile(
                userId          = userId,
                totalPoints     = 0,
                currentStreak   = 0,
                maxStreak       = 0,
                lastSessionDate = null,
                badges          = emptyList()
            )
            gamificationDao.insertProfile(blank.toEntity())
            blank
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun requireProfile(userId: String): GamificationProfile =
        gamificationDao.getProfile(userId)?.toDomain()
            ?: throw NoSuchElementException("Gamification profile not found for user: $userId")
}
