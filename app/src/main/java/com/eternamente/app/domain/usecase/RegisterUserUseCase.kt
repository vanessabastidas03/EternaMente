package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.repository.GamificationRepository
import com.eternamente.app.domain.repository.UserRepository

/**
 * Orchestrates the full user registration flow as a single atomic operation.
 *
 * Steps executed in order:
 * 1. Persist the [User] record via [UserRepository.registerUser].
 * 2. Record informed consent via [UserRepository.recordConsent] (if [consentTimestamp] provided).
 * 3. Initialize a blank gamification profile via [GamificationRepository.initializeProfile].
 *
 * If any step fails, the use case returns [Result.Error] immediately. Callers are
 * responsible for compensating (e.g., deleting the Firebase Auth account) if needed.
 *
 * @property userRepository Handles user persistence and consent recording.
 * @property gamificationRepository Handles gamification profile initialization.
 */
class RegisterUserUseCase(
    private val userRepository: UserRepository,
    private val gamificationRepository: GamificationRepository
) {

    /**
     * Executes the registration flow.
     *
     * @param user The new [User] to register. The [User.id] must equal the
     *   Firebase Auth UID pre-assigned by the Auth layer.
     * @param consentTimestamp Epoch-millisecond timestamp of informed-consent
     *   acceptance; pass `null` if consent will be collected in a separate step.
     * @return [Result.Success] with the registered [User], or [Result.Error] on any
     *   step failure (user save, consent record, or gamification init).
     */
    suspend operator fun invoke(user: User, consentTimestamp: Long?): Result<User> {
        TODO("Not yet implemented")
    }
}
