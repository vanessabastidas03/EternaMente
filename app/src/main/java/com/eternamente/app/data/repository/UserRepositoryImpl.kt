package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.db.dao.UserDao
import com.eternamente.app.data.local.db.entity.toDomain
import com.eternamente.app.data.local.db.entity.toEntity
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Concrete implementation of [UserRepository].
 *
 * Local persistence: Room via [UserDao].
 * Authentication: Firebase Auth via [FirebaseAuth].
 * No remote document store (Firestore) is required for this data scope.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val firebaseAuth: FirebaseAuth
) : UserRepository {

    override suspend fun registerUser(user: User): Result<User> = safeCall {
        userDao.insertUser(user.toEntity())
        user
    }

    override suspend fun getUserById(userId: String): Result<User> = safeCall {
        userDao.getUserById(userId)?.toDomain()
            ?: throw NoSuchElementException("User not found: $userId")
    }

    override fun observeCurrentUser(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.transformLatest { uid ->
        // Suspend-safe: transformLatest cancels previous block on each new emission
        emit(uid?.let { userDao.getUserById(it)?.toDomain() })
    }

    override suspend fun login(email: String, password: String): Result<User> = safeCall {
        val authResult = suspendCancellableCoroutine { cont ->
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
                .addOnCanceledListener { cont.cancel() }
        }
        val uid = authResult.user?.uid
            ?: throw IllegalStateException("Login succeeded but UID is null")
        userDao.getUserById(uid)?.toDomain()
            ?: throw NoSuchElementException("User profile not found after login: $uid")
    }

    override suspend fun logout(): Result<Unit> = safeCall {
        firebaseAuth.signOut()
    }

    override suspend fun updateUser(user: User): Result<User> = safeCall {
        userDao.updateUser(user.toEntity())
        user
    }

    override suspend fun recordConsent(userId: String, timestamp: Long): Result<Unit> = safeCall {
        userDao.updateConsentTimestamp(userId, timestamp)
    }

    override suspend fun getUserByEmail(email: String): Result<User?> = safeCall {
        userDao.getUserByEmail(email.lowercase().trim())?.toDomain()
    }

    override suspend fun deleteAccount(userId: String): Result<Unit> = safeCall {
        userDao.deleteUser(userId)
        firebaseAuth.currentUser?.let { fbUser ->
            suspendCancellableCoroutine { cont ->
                fbUser.delete()
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
                    .addOnCanceledListener { cont.cancel() }
            }
        }
    }
}
