package com.eternamente.app.domain.model

/**
 * Gamification state for a user, tracking engagement metrics and earned achievements.
 *
 * The gamification layer is designed to motivate consistent participation without
 * creating anxiety around cognitive scores. Points and badges are awarded for
 * participation and improvement, not for absolute performance levels.
 *
 * @property userId Identifier of the [User] who owns this profile.
 * @property totalPoints Cumulative points earned across all sessions since registration.
 * @property currentStreak Number of consecutive calendar days in which the user completed
 *   at least one [CognitiveSession]. Resets to 0 after a missed day.
 * @property maxStreak Historical maximum consecutive-day streak.
 * @property lastSessionDate ISO-8601 date string (e.g., `"2024-03-15"`) of the most recent
 *   completed session; `null` if the user has never finished a session.
 * @property badges Ordered list of [Badge] achievements the user has unlocked.
 *   Ordering reflects unlock chronology (oldest first).
 */
data class GamificationProfile(
    val userId: String,
    val totalPoints: Int,
    val currentStreak: Int,
    val maxStreak: Int,
    val lastSessionDate: String?,
    val badges: List<Badge>
) {

    /** Returns `true` if the user has unlocked the given [badge]. */
    fun hasBadge(badge: Badge): Boolean = badge in badges

    /** Returns the number of remaining badges not yet unlocked. */
    val remainingBadges: Int get() = Badge.entries.size - badges.size

    /**
     * Returns a copy of this profile with [pointsToAdd] added to [totalPoints].
     * Pure function — does not mutate; use in repository implementations.
     */
    fun withAddedPoints(pointsToAdd: Int): GamificationProfile =
        copy(totalPoints = totalPoints + pointsToAdd)
}
