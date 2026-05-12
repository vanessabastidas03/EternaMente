package com.eternamente.app.domain.model

/**
 * Represents a single cognitive evaluation session completed by a user.
 *
 * A session is the top-level container for one or more [GameResult] records.
 * Sessions can be interrupted (incomplete) and resumed only for [SessionType.DAILY].
 *
 * @property id Unique identifier (UUID v4) assigned when the session is started.
 * @property userId Identifier of the [User] who owns this session.
 * @property sessionDate Start timestamp of the session in epoch milliseconds (UTC).
 * @property durationSeconds Total wall-clock duration in seconds from start to end;
 *   `null` if the session was interrupted and never completed.
 * @property type Classification of the session — see [SessionType].
 * @property completed `true` if the user reached the final game and submitted results;
 *   `false` if the session was abandoned or is still in progress.
 */
data class CognitiveSession(
    val id: String,
    val userId: String,
    val sessionDate: Long,
    val durationSeconds: Int?,
    val type: SessionType,
    val completed: Boolean
) {

    /**
     * Returns `true` if this session is a completed baseline assessment.
     * A completed baseline is required before daily sessions are unlocked.
     */
    val isCompletedBaseline: Boolean
        get() = type == SessionType.BASELINE && completed

    /**
     * Returns the session duration as a human-readable string (e.g., "12m 30s"),
     * or `"–"` if the session was not completed.
     */
    val formattedDuration: String
        get() = durationSeconds
            ?.let { s -> "${s / 60}m ${s % 60}s" }
            ?: "–"
}
