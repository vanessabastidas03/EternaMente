package com.eternamente.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eternamente.app.data.local.db.entity.UserCredentialsEntity

/** Room DAO para [UserCredentialsEntity] — credenciales de autenticación local. */
@Dao
interface UserCredentialsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredentials(credentials: UserCredentialsEntity)

    @Query("SELECT * FROM user_credentials WHERE userId = :userId LIMIT 1")
    suspend fun getCredentialsByUserId(userId: String): UserCredentialsEntity?

    @Query("""
        UPDATE user_credentials
        SET failedLoginAttempts = :attempts, lockedUntil = :lockedUntil
        WHERE userId = :userId
    """)
    suspend fun updateFailedAttempts(userId: String, attempts: Int, lockedUntil: Long?)

    @Query("DELETE FROM user_credentials WHERE userId = :userId")
    suspend fun deleteCredentials(userId: String)
}
