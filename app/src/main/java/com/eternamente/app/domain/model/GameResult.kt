package com.eternamente.app.domain.model

/**
 * Result produced by a single cognitive mini-game within a [CognitiveSession].
 *
 * All scores and timing metrics are stored as computed at game-end.
 * Normalization is applied by the scoring engine in the data layer before
 * persisting, so [scoreNormalized] is always ready for display and ML input.
 *
 * @property id Unique identifier (UUID v4).
 * @property sessionId Identifier of the parent [CognitiveSession].
 * @property gameId Stable identifier for the mini-game (e.g., `"digit_span"`, `"stroop"`).
 *   Used to group longitudinal performance for a specific task.
 * @property domain Cognitive domain evaluated by this game — see [CognitiveDomain].
 * @property scoreRaw Raw numeric score as returned by the game engine (scale varies by game).
 * @property scoreNormalized Score normalized to a 0–100 scale, adjusted for the user's
 *   age and education cohort. This is the primary value used in ML features.
 * @property reactionTimeMsAvg Arithmetic mean of per-stimulus reaction times in milliseconds.
 * @property reactionTimeMsP50 Median (P50) reaction time in milliseconds.
 *   Less sensitive to outlier fast/slow responses than [reactionTimeMsAvg].
 * @property accuracyPct Percentage of correct responses in the range [0.0, 100.0].
 * @property errorsCount Total number of incorrect or missed responses.
 * @property difficultyLevel Adaptive difficulty level reached at game end (1 = easiest).
 *   For fixed-difficulty games, this is always 1.
 */
data class GameResult(
    val id: String,
    val sessionId: String,
    val gameId: String,
    val domain: CognitiveDomain,
    val scoreRaw: Float,
    val scoreNormalized: Float,
    val reactionTimeMsAvg: Float,
    val reactionTimeMsP50: Float,
    val accuracyPct: Float,
    val errorsCount: Int,
    val difficultyLevel: Int
) {

    /**
     * Returns `true` if the accuracy exceeds 90% — used to evaluate badge eligibility.
     */
    val isPerfect: Boolean get() = accuracyPct >= 90f

    /**
     * Returns `true` if the normalized score is above the normative mean (50 points).
     */
    val isAboveAverage: Boolean get() = scoreNormalized > 50f
}
