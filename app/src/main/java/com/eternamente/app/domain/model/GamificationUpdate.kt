package com.eternamente.app.domain.model

/**
 * Result of a gamification update after a completed session.
 *
 * @property profile            Updated [GamificationProfile] post-session.
 * @property pointsAwarded      Total points credited in this update.
 * @property newlyUnlockedBadges Badges earned for the first time in this update.
 *                               Empty if no new achievements were unlocked.
 */
data class GamificationUpdate(
    val profile: GamificationProfile,
    val pointsAwarded: Int,
    val newlyUnlockedBadges: List<Badge>
)
