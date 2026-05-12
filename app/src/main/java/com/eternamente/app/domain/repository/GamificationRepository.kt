package com.eternamente.app.domain.repository

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.GamificationProfile
import kotlinx.coroutines.flow.Flow

/**
 * Contract for gamification state persistence and badge lifecycle management.
 *
 * All mutations are idempotent: calling [unlockBadge] with an already-earned badge
 * is a no-op. The data layer implementation uses Room transactions to ensure
 * atomic updates to [GamificationProfile] fields.
 */
interface GamificationRepository {

    /**
     * Returns the [GamificationProfile] for a user from the local database.
     *
     * @param userId UUID of the target user.
     * @return [Result.Success] with the profile, or [Result.Error] if the profile
     *   has not yet been initialized (see [initializeProfile]).
     */
    suspend fun getProfile(userId: String): Result<GamificationProfile>

    /**
     * Emits the [GamificationProfile] for a user, updating in real time on any change.
     *
     * Suitable for binding to the home screen UI to reflect point and badge updates
     * immediately after a session completes.
     *
     * @param userId UUID of the target user.
     */
    fun observeProfile(userId: String): Flow<GamificationProfile?>

    /**
     * Atomically adds [pointsToAdd] to the user's [GamificationProfile.totalPoints].
     *
     * @param userId UUID of the target user.
     * @param pointsToAdd Number of points earned. Must be non-negative.
     * @return [Result.Success] with the updated [GamificationProfile], or [Result.Error] on failure.
     */
    suspend fun addPoints(userId: String, pointsToAdd: Int): Result<GamificationProfile>

    /**
     * Recalculates and updates [GamificationProfile.currentStreak] and
     * [GamificationProfile.maxStreak] based on [sessionDateIso].
     *
     * The streak increments if [sessionDateIso] is the calendar day immediately
     * following [GamificationProfile.lastSessionDate]; resets to 1 otherwise.
     *
     * @param userId UUID of the target user.
     * @param sessionDateIso ISO-8601 date of the completed session (e.g., `"2024-03-15"`).
     * @return [Result.Success] with the updated profile, or [Result.Error] on failure.
     */
    suspend fun updateStreak(userId: String, sessionDateIso: String): Result<GamificationProfile>

    /**
     * Appends [badge] to the user's earned badge list if not already present.
     *
     * Idempotent: calling this with a badge the user already holds returns
     * [Result.Success] with the unchanged profile.
     *
     * @param userId UUID of the target user.
     * @param badge The [Badge] to unlock.
     * @return [Result.Success] with the (possibly updated) [GamificationProfile].
     */
    suspend fun unlockBadge(userId: String, badge: Badge): Result<GamificationProfile>

    /**
     * Creates a blank [GamificationProfile] for a newly registered user.
     *
     * Called once, immediately after [UserRepository.registerUser] succeeds.
     * Subsequent calls for the same [userId] should be no-ops.
     *
     * @param userId UUID of the new user.
     * @return [Result.Success] with the initialized profile, or [Result.Error] on failure.
     */
    suspend fun initializeProfile(userId: String): Result<GamificationProfile>
}
