package com.eternamente.app.data.repository

import com.eternamente.app.core.Result
import com.eternamente.app.core.safeCall
import com.eternamente.app.data.local.database.dao.UserCredentialsDao
import com.eternamente.app.data.local.database.entity.toDomain
import com.eternamente.app.data.local.database.entity.toEntity
import com.eternamente.app.domain.model.UserCredentials
import com.eternamente.app.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [AuthRepository] respaldada por Room + SQLCipher.
 *
 * Toda la lógica de hashing vive en [com.eternamente.app.data.local.crypto.CryptoManager];
 * este repositorio solo persiste y recupera los hashes ya derivados.
 *
 * Usa [com.eternamente.app.data.local.database.dao.UserCredentialsDao] del nuevo
 * paquete `data.local.database` (provisto por [com.eternamente.app.di.DatabaseModule]).
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userCredentialsDao: UserCredentialsDao   // data.local.database.dao
) : AuthRepository {

    override suspend fun saveCredentials(credentials: UserCredentials): Result<Unit> =
        withContext(Dispatchers.IO) {
            safeCall { userCredentialsDao.insert(credentials.toEntity()) }
        }

    override suspend fun getCredentialsByUserId(userId: String): Result<UserCredentials?> =
        withContext(Dispatchers.IO) {
            safeCall { userCredentialsDao.getByUserId(userId)?.toDomain() }
        }

    override suspend fun updateFailedAttempts(
        userId: String,
        attempts: Int,
        lockedUntilMs: Long?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        safeCall { userCredentialsDao.updateFailedAttempts(userId, attempts, lockedUntilMs) }
    }

    override suspend fun deleteCredentials(userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            safeCall { userCredentialsDao.deleteByUserId(userId) }
        }
}
