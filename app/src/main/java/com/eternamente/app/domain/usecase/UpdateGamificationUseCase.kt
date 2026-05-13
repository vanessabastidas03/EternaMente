package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.core.getOrNull
import com.eternamente.app.core.getOrThrow
import com.eternamente.app.core.safeCall
import com.eternamente.app.core.notifications.BadgeNotificationHelper
import com.eternamente.app.domain.gamification.BadgeStats
import com.eternamente.app.domain.gamification.GamificationEngine
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.model.GamificationUpdate
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.GamificationRepository
import com.eternamente.app.domain.repository.SessionRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Processes a completed session and applies all gamification updates:
 * 1. **Points** — computed per-game using [GamificationEngine.calculatePoints].
 * 2. **Streak** — updated via [GamificationEngine.calculateStreakResult].
 * 3. **Badges** — all 13 badge conditions evaluated via [GamificationEngine.checkBadgeUnlocks].
 * 4. **Notifications** — a local push notification fires for each newly unlocked badge.
 *
 * @return [Result.Success] with a [GamificationUpdate] containing the updated profile,
 *   total points awarded, and list of newly unlocked badges.
 */
class UpdateGamificationUseCase @Inject constructor(
    private val gamificationRepository: GamificationRepository,
    private val gameResultRepository: GameResultRepository,
    private val sessionRepository: SessionRepository,
    private val notificationHelper: BadgeNotificationHelper
) {

    private val engine = GamificationEngine()

    suspend operator fun invoke(
        session: CognitiveSession,
        results: List<GameResult>
    ): Result<GamificationUpdate> = safeCall {
        val userId = session.userId
        require(userId.isNotBlank()) { "userId cannot be empty" }

        // ── 1. Current profile — needed for streak multiplier ─────────────────
        val profileBefore = gamificationRepository.getProfile(userId).getOrThrow()

        // ── 2. Calculate and award points ─────────────────────────────────────
        val totalPoints = results.sumOf { engine.calculatePoints(it, profileBefore.currentStreak) }
            .coerceAtLeast(1)
        gamificationRepository.addPoints(userId, totalPoints).getOrThrow()

        // ── 3. Update streak ──────────────────────────────────────────────────
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val streakResult = engine.calculateStreakResult(
            lastSessionDate = profileBefore.lastSessionDate,
            sessionDate     = today,
            currentStreak   = profileBefore.currentStreak
        )
        if (streakResult !is GamificationEngine.StreakResult.AlreadyDone) {
            gamificationRepository.updateStreak(userId, today).getOrThrow()
        }

        // ── 4. Load stats and check badge unlocks ─────────────────────────────
        val profileAfterUpdate = gamificationRepository.getProfile(userId).getOrThrow()
        val stats  = loadBadgeStats(userId)
        val newBadges = engine.checkBadgeUnlocks(profileAfterUpdate, stats)

        for (badge in newBadges) {
            gamificationRepository.unlockBadge(userId, badge).getOrThrow()
            notificationHelper.showBadgeUnlocked(badge)
        }

        // ── 5. Return final state ─────────────────────────────────────────────
        val finalProfile = gamificationRepository.getProfile(userId).getOrThrow()
        GamificationUpdate(
            profile             = finalProfile,
            pointsAwarded       = totalPoints,
            newlyUnlockedBadges = newBadges
        )
    }

    // ── Badge stats loader ────────────────────────────────────────────────────

    private suspend fun loadBadgeStats(userId: String): BadgeStats {
        val totalSessions = sessionRepository.countAllCompletedSessions(userId).getOrNull() ?: 0
        val memAbove90    = gameResultRepository.countMemoryGamesAboveAccuracy(userId, 90f).getOrNull() ?: 0
        val attPerfect    = gameResultRepository.countAttentionGamesPerfect(userId).getOrNull() ?: 0
        val uniqueDomains = gameResultRepository.countUniqueDomains(userId).getOrNull() ?: 0
        val maxDiff       = gameResultRepository.maxDifficultyReached(userId).getOrNull() ?: 1
        val flashMinRt    = gameResultRepository.flashColorMinRtMs(userId).getOrNull() ?: 0f
        val baselineDone  = if (sessionRepository.hasCompletedBaseline(userId).getOrNull() == true) 1 else 0
        val longestGap    = computeLongestGapDays(userId)

        return BadgeStats(
            totalSessionsCompleted    = totalSessions,
            memoryGamesAbove90        = memAbove90,
            attentionGamesPerfect     = attPerfect,
            uniqueDomainsTried        = uniqueDomains,
            maxDifficultyReached      = maxDiff,
            flashColorMinRtMs         = flashMinRt,
            baselineSessionsCompleted = baselineDone,
            longestGapDays            = longestGap,
            reportsGenerated          = 0
        )
    }

    private suspend fun computeLongestGapDays(userId: String): Int {
        val dates = sessionRepository.getAllSessionDates(userId).getOrNull() ?: return 0
        if (dates.size < 2) return 0
        var maxGap = 0
        for (i in 1 until dates.size) {
            val gap = TimeUnit.MILLISECONDS.toDays(dates[i] - dates[i - 1]).toInt()
            if (gap > maxGap) maxGap = gap
        }
        return maxGap
    }
}
