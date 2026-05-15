package com.eternamente.app.domain.usecase

import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Terminates the current user session.
 *
 * After this runs:
 * - Firebase Auth session is invalidated via [UserRepository.logout].
 * - [UserPreferencesRepository.KEY_IS_LOGGED_IN] is `false`.
 * - [UserPreferencesRepository.KEY_CURRENT_USER_ID] is removed.
 *
 * Room data (game history, gamification) is intentionally **preserved** so
 * the user can log back in and see their progress.
 */
class LogoutUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke() {
        userRepository.logout()
        userPreferencesRepository.updateIsLoggedIn(false)
        userPreferencesRepository.updateCurrentUserId(null)
    }
}
