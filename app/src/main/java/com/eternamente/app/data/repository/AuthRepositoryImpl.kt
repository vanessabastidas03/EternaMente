package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.db.dao.UserCredentialsDao
import com.eternamente.app.data.local.db.entity.toDomain
import com.eternamente.app.data.local.db.entity.toEntity
import com.eternamente.app.domain.model.UserCredentials
import com.eternamente.app.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [AuthRepository] respaldada por Room + SQLCipher.
 *
 * Toda la lógica de hashing vive en [CryptoManager]; este repositorio
 * solo persiste y recupera los hashes ya derivados.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userCredentialsDao: UserCredentialsDao
) : AuthRepository {

    override suspend fun saveCredentials(credentials: UserCredentials): Result<Unit> = safeCall {
        userCredentialsDao.insertCredentials(credentials.toEntity())
    }

    override suspend fun getCredentialsByUserId(userId: String): Result<UserCredentials?> = safeCall {
        userCredentialsDao.getCredentialsByUserId(userId)?.toDomain()
    }

    override suspend fun updateFailedAttempts(
        userId: String,
        attempts: Int,
        lockedUntilMs: Long?
    ): Result<Unit> = safeCall {
        userCredentialsDao.updateFailedAttempts(userId, attempts, lockedUntilMs)
    }

    override suspend fun deleteCredentials(userId: String): Result<Unit> = safeCall {
        userCredentialsDao.deleteCredentials(userId)
    }
}
