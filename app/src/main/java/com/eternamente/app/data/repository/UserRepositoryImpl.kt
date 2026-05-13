package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.database.dao.UserDao
import com.eternamente.app.data.local.database.entity.toDomain
import com.eternamente.app.data.local.database.entity.toEntity
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val firebaseAuth: FirebaseAuth
) : UserRepository {

    override suspend fun registerUser(user: User): Result<User> = withContext(Dispatchers.IO) {
        safeCall {
            userDao.insert(user.toEntity())
            user
        }
    }

    override suspend fun getUserById(userId: String): Result<User> = withContext(Dispatchers.IO) {
        safeCall {
            userDao.getById(userId)?.toDomain()
                ?: throw NoSuchElementException("User not found: $userId")
        }
    }

    override suspend fun getUserByEmail(email: String): Result<User?> = withContext(Dispatchers.IO) {
        safeCall { userDao.getByEmail(email.lowercase().trim())?.toDomain() }
    }

    override fun observeCurrentUser(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.map { uid ->
        uid?.let { withContext(Dispatchers.IO) { userDao.getById(it)?.toDomain() } }
    }.flowOn(Dispatchers.IO)

    override suspend fun login(email: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            safeCall {
                val result = suspendCancellableCoroutine { cont ->
                    firebaseAuth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                        .addOnCanceledListener { cont.cancel() }
                }
                val uid = result.user?.uid ?: throw IllegalStateException("UID null after login")
                userDao.getById(uid)?.toDomain()
                    ?: throw NoSuchElementException("User profile not found: $uid")
            }
        }

    override suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        safeCall { firebaseAuth.signOut() }
    }

    override suspend fun updateUser(user: User): Result<User> = withContext(Dispatchers.IO) {
        safeCall {
            userDao.update(user.toEntity())
            user
        }
    }

    override suspend fun recordConsent(userId: String, timestamp: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            safeCall { userDao.updateConsentTimestamp(userId, timestamp) }
        }

    override suspend fun deleteAccount(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeCall {
            userDao.deleteById(userId)
            val fbUser = firebaseAuth.currentUser
            if (fbUser != null) {
                suspendCancellableCoroutine { cont ->
                    fbUser.delete()
                        .addOnSuccessListener { cont.resume(Unit) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                        .addOnCanceledListener { cont.cancel() }
                }
            }
        }
    }
}
