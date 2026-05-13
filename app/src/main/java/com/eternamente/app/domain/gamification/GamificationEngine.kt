package com.eternamente.app.domain.gamification

import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.model.GamificationProfile
import java.time.LocalDate

/**
 * Pure, stateless computation engine for gamification logic.
 *
 * All methods are deterministic and side-effect-free — they only compute values
 * from their inputs. Persistence is handled by [GamificationRepository].
 */
class GamificationEngine {

    // ── Point calculation ─────────────────────────────────────────────────────

    /**
     * Calculates the points earned for a single game result.
     *
     * Formula:
     * - basePoints = accuracy% × 10  (0–100 pts)
     * - speedBonus = 20 if RT < 800 ms | 10 if RT < 1500 ms | 0 otherwise
     * - streakMultiplier = 1 + (streak.coercedTo7 × 0.1)  → max 1.7×
     * - result = (basePoints + speedBonus) × streakMultiplier, rounded down
     *
     * @param result        The completed game result with accuracy and RT data.
     * @param currentStreak The user's current consecutive-day streak before this session.
     */
    fun calculatePoints(result: GameResult, currentStreak: Int): Int {
        val basePoints = (result.accuracyPct * 10f).toInt().coerceIn(0, 100)
        val speedBonus = when {
            result.reactionTimeMsAvg < 800f  -> 20
            result.reactionTimeMsAvg < 1500f -> 10
            else                              -> 0
        }
        val streakMultiplier = 1f + (currentStreak.coerceAtMost(7) * 0.1f)
        return ((basePoints + speedBonus) * streakMultiplier).toInt()
    }

    // ── Streak calculation ────────────────────────────────────────────────────

    /** Result of evaluating a streak update. */
    sealed class StreakResult {
        /** User already completed a session today — no change. */
        object AlreadyDone : StreakResult()
        /** Streak extended by one day. */
        data class Continued(val newStreak: Int) : StreakResult()
        /** New streak started (first session ever, or gap > 1 day). */
        data class Started(val streak: Int = 1) : StreakResult()
    }

    /**
     * Determines how the streak should change given the last session date and today's date.
     *
     * @param lastSessionDate ISO-8601 date of the last session, or `null` if none.
     * @param sessionDate     ISO-8601 date of the current session being evaluated.
     * @param currentStreak   The streak value stored in [GamificationProfile].
     */
    fun calculateStreakResult(
        lastSessionDate: String?,
        sessionDate: String,
        currentStreak: Int
    ): StreakResult {
        val today = LocalDate.parse(sessionDate)
        val last  = lastSessionDate?.let { LocalDate.parse(it) }
        return when {
            last == today              -> StreakResult.AlreadyDone
            last == today.minusDays(1) -> StreakResult.Continued(currentStreak + 1)
            else                       -> StreakResult.Started(1)
        }
    }

    // ── Badge eligibility ─────────────────────────────────────────────────────

    /**
     * Evaluates which badges the user should have but doesn't yet hold.
     *
     * Purely based on the provided [profile] and [stats] — does not call any
     * repository. The caller is responsible for persisting new badges.
     *
     * @param profile Current gamification profile (to check existing badges).
     * @param stats   Pre-aggregated stats needed for condition evaluation.
     * @return List of badges the user is now eligible for but hasn't yet unlocked.
     */
    fun checkBadgeUnlocks(
        profile: GamificationProfile,
        stats: BadgeStats
    ): List<Badge> = buildList {
        fun check(badge: Badge, condition: Boolean) {
            if (!profile.hasBadge(badge) && condition) add(badge)
        }

        check(Badge.FIRST_STEP,         stats.totalSessionsCompleted >= 1)
        check(Badge.WEEK_WARRIOR,        profile.currentStreak >= 7)
        check(Badge.CONSISTENT,          profile.currentStreak >= 14)
        check(Badge.CONSISTENCY_MASTER,  profile.currentStreak >= 30)
        check(Badge.MEMORY_ACE,          stats.memoryGamesAbove90 >= 3)
        check(Badge.ATTENTION_CHAMPION,  stats.attentionGamesPerfect >= 1)
        check(Badge.DOMAIN_EXPLORER,     stats.uniqueDomainsTried >= ALL_DOMAINS_COUNT)
        check(Badge.LEVEL_MAX,           stats.maxDifficultyReached >= 5)
        check(Badge.SPEED_DEMON,         stats.flashColorMinRtMs < 500f && stats.flashColorMinRtMs > 0f)
        check(Badge.FULL_SPRINT,         stats.baselineSessionsCompleted >= 1)
        check(Badge.COMEBACK,            stats.longestGapDays >= 7 && stats.totalSessionsCompleted >= 2)
        check(Badge.FIRST_REPORT,        stats.reportsGenerated >= 1)
    }

    /**
     * Returns a progress percentage (0–100) toward unlocking [badge].
     * Used to show partial-progress indicators in the UI for unearned badges.
     */
    fun badgeProgress(badge: Badge, profile: GamificationProfile, stats: BadgeStats): Int =
        when (badge) {
            Badge.FIRST_STEP        -> if (stats.totalSessionsCompleted >= 1) 100 else 0
            Badge.WEEK_WARRIOR      -> (profile.currentStreak * 100 / 7).coerceAtMost(100)
            Badge.CONSISTENT        -> (profile.currentStreak * 100 / 14).coerceAtMost(100)
            Badge.CONSISTENCY_MASTER-> (profile.currentStreak * 100 / 30).coerceAtMost(100)
            Badge.MEMORY_ACE        -> (stats.memoryGamesAbove90 * 100 / 3).coerceAtMost(100)
            Badge.ATTENTION_CHAMPION-> if (stats.attentionGamesPerfect >= 1) 100 else 0
            Badge.DOMAIN_EXPLORER   -> (stats.uniqueDomainsTried * 100 / ALL_DOMAINS_COUNT).coerceAtMost(100)
            Badge.LEVEL_MAX         -> (stats.maxDifficultyReached * 100 / 5).coerceAtMost(100)
            Badge.SPEED_DEMON       -> if (stats.flashColorMinRtMs in 1f..499f) 100 else
                                         if (stats.flashColorMinRtMs <= 0f) 0
                                         else ((1f - (stats.flashColorMinRtMs - 499f) / 1001f) * 100f).toInt().coerceIn(0, 99)
            Badge.FULL_SPRINT       -> if (stats.baselineSessionsCompleted >= 1) 100 else 0
            Badge.COMEBACK          -> if (stats.longestGapDays >= 7) 100 else (stats.longestGapDays * 100 / 7).coerceAtMost(99)
            Badge.FIRST_REPORT      -> if (stats.reportsGenerated >= 1) 100 else 0
            Badge.EARLY_ADOPTER     -> 100
        }

    private companion object {
        const val ALL_DOMAINS_COUNT = 6  // MEMORY, ATTENTION, EXECUTIVE, LANGUAGE, VISUOSPATIAL, PROCESSING_SPEED
    }
}
