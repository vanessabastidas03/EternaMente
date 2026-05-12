package com.eternamente.app.domain.repository

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Contract for user persistence, authentication, and consent management.
 *
 * Implementations live in the data layer (`:data` module or `data` package)
 * and coordinate between Room (local) and Firebase Auth (remote).
 * The domain layer depends only on this interface — never on concrete implementations.
 */
interface UserRepository {

    /**
     * Creates a new [User] record in the local database and Firebase Firestore.
     *
     * Should be called after Firebase Auth account creation is confirmed.
     *
     * @param user The [User] entity to persist. The [User.id] must match
     *   the Firebase Auth UID assigned during account creation.
     * @return [Result.Success] with the stored [User], or [Result.Error] on I/O failure.
     */
    suspend fun registerUser(user: User): Result<User>

    /**
     * Retrieves a [User] by their unique identifier from the local cache.
     * Falls back to Firestore if the user is not found locally.
     *
     * @param userId The UUID (Firebase Auth UID) of the target user.
     * @return [Result.Success] with the [User], or [Result.Error] if not found.
     */
    suspend fun getUserById(userId: String): Result<User>

    /**
     * Emits the currently authenticated user, or `null` if no session is active.
     *
     * The Flow updates reactively on Firebase Auth state changes (sign-in/sign-out)
     * and on local profile edits. Callers should collect from [kotlinx.coroutines.Dispatchers.Main].
     */
    fun observeCurrentUser(): Flow<User?>

    /**
     * Authenticates an existing user via Firebase Auth email/password.
     *
     * On success, loads and caches the user's profile from Firestore.
     *
     * @param email The user's registered email address.
     * @param password The plaintext password (never stored or logged by the domain layer).
     * @return [Result.Success] with the authenticated [User], or [Result.Error] on
     *   invalid credentials, network failure, or disabled account.
     */
    suspend fun login(email: String, password: String): Result<User>

    /**
     * Signs the current user out of Firebase Auth and clears the local session cache.
     *
     * @return [Result.Success] on completion, or [Result.Error] if sign-out fails.
     */
    suspend fun logout(): Result<Unit>

    /**
     * Persists changes to mutable [User] fields (name, age, educationYears, gender).
     *
     * Updates both the local Room database and the remote Firestore document.
     *
     * @param user The [User] with updated fields. [User.id] must match an existing record.
     * @return [Result.Success] with the updated [User], or [Result.Error] on failure.
     */
    suspend fun updateUser(user: User): Result<User>

    /**
     * Records the informed-consent acceptance timestamp for the given user.
     *
     * Sets [User.consentGivenAt] to [timestamp] and persists the change.
     * No cognitive data may be collected until this has been called successfully.
     *
     * @param userId The UUID of the consenting user.
     * @param timestamp Epoch-millisecond timestamp of consent acceptance.
     * @return [Result.Success] on update, or [Result.Error] on failure.
     */
    suspend fun recordConsent(userId: String, timestamp: Long): Result<Unit>

    /**
     * Deletes the user's account, all local data, and the remote Firestore document.
     *
     * This operation is irreversible. Implementations must also revoke the
     * Firebase Auth account and clear all cached data.
     *
     * @param userId The UUID of the user requesting deletion.
     * @return [Result.Success] on complete deletion, or [Result.Error] on partial failure.
     */
    suspend fun deleteAccount(userId: String): Result<Unit>
}
