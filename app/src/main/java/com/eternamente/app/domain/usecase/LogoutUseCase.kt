package com.eternamente.app.domain.usecase

import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import javax.inject.Inject

/**
 * Terminates the current user session by clearing all authentication state from DataStore.
 *
 * After this runs:
 * - [UserPreferencesRepository.KEY_IS_LOGGED_IN] is `false`
 * - [UserPreferencesRepository.KEY_CURRENT_USER_ID] is removed
 *
 * Room data (game history, gamification) is intentionally **preserved** so
 * the user can log back in and see their progress.
 */
class LogoutUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke() {
        userPreferencesRepository.updateIsLoggedIn(false)
        userPreferencesRepository.updateCurrentUserId(null)
    }
}
