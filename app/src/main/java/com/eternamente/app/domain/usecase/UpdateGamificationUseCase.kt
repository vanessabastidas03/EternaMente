package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.model.GamificationProfile
import com.eternamente.app.domain.repository.GamificationRepository

/**
 * Evaluates a completed session and its results, then awards points and unlocks badges.
 *
 * Gamification rule set applied in sequence:
 * 1. **Points**: base points for session completion + bonus for accuracy and difficulty.
 * 2. **Streak**: updates [GamificationProfile.currentStreak] based on session date.
 * 3. **Badges**: evaluates eligibility for all [Badge] values and unlocks any newly earned.
 *
 * All mutations are delegated to [GamificationRepository] and are idempotent —
 * calling this use case twice with the same session is safe.
 *
 * @property gamificationRepository Handles all gamification state mutations.
 */
class UpdateGamificationUseCase(
    private val gamificationRepository: GamificationRepository
) {

    /**
     * Processes a completed session and applies all gamification updates.
     *
     * @param session The [CognitiveSession] that was just completed.
     *   [CognitiveSession.completed] must be `true` before calling this use case.
     * @param results All [GameResult] entities produced during [session].
     *   An empty list is valid (e.g., the user completed the session with zero games).
     * @return [Result.Success] with the updated [GamificationProfile], or [Result.Error]
     *   if any repository operation fails.
     */
    suspend operator fun invoke(
        session: CognitiveSession,
        results: List<GameResult>
    ): Result<GamificationProfile> {
        TODO("Not yet implemented")
    }

    companion object {
        /** Base points awarded for completing any session type. */
        const val BASE_POINTS_PER_SESSION = 10

        /** Bonus points awarded per game result that has [GameResult.isPerfect] == true. */
        const val BONUS_POINTS_PERFECT_GAME = 5

        /** Bonus points per difficulty level above the minimum for a given game result. */
        const val BONUS_POINTS_PER_DIFFICULTY_LEVEL = 2
    }
}
