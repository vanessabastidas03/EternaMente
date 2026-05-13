package com.eternamente.app.domain.gamification

/**
 * Pre-aggregated statistics needed by [GamificationEngine.checkBadgeUnlocks].
 *
 * Populated by the data layer from Room queries before calling the engine,
 * so the engine itself stays free of I/O dependencies.
 */
data class BadgeStats(
    /** Total cognitive sessions ever completed by the user. */
    val totalSessionsCompleted: Int = 0,
    /** Memory games where accuracy ≥ 90%. */
    val memoryGamesAbove90: Int = 0,
    /** Attention games where accuracy ≥ 90% (for ATTENTION_CHAMPION). */
    val attentionGamesPerfect: Int = 0,
    /** Number of distinct cognitive domains the user has played at least once. */
    val uniqueDomainsTried: Int = 0,
    /** Highest difficulty level ever reached across all games. */
    val maxDifficultyReached: Int = 1,
    /** Best (lowest) reaction time achieved in "flash_color" game; 0 if never played. */
    val flashColorMinRtMs: Float = 0f,
    /** Number of BASELINE or WEEKLY_FULL sessions completed. */
    val baselineSessionsCompleted: Int = 0,
    /** Longest gap in days between any two consecutive sessions (for COMEBACK). */
    val longestGapDays: Int = 0,
    /** Number of times the weekly/cognitive report has been generated. */
    val reportsGenerated: Int = 0
)
