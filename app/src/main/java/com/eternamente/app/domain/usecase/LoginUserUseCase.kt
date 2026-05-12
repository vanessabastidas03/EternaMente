package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.repository.UserRepository

/**
 * Authenticates an existing user via Firebase Auth and loads their local profile.
 *
 * This use case is intentionally thin: authentication logic belongs to the data layer.
 * The domain layer's responsibility is to ensure the authenticated identity is valid,
 * has accepted consent, and has a local [User] record.
 *
 * @property userRepository Handles Firebase Auth delegation and local user retrieval.
 */
class LoginUserUseCase(
    private val userRepository: UserRepository
) {

    /**
     * Executes the authentication flow.
     *
     * @param email The user's registered email address.
     * @param password The plaintext password (passed directly to Firebase Auth;
     *   never stored or logged by the domain layer).
     * @return [Result.Success] with the authenticated [User], or [Result.Error] on
     *   invalid credentials, unverified email, disabled account, or network failure.
     */
    suspend operator fun invoke(email: String, password: String): Result<User> {
        TODO("Not yet implemented")
    }
}
