package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.UserCredentials

/** Credenciales de autenticación local — PIN hasheado + salt. NUNCA almacenar el PIN plano. */
@Entity(
    tableName    = "user_credentials",
    foreignKeys  = [
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["userId"],
            onDelete      = ForeignKey.CASCADE
        )
    ]
)
data class UserCredentialsEntity(
    @PrimaryKey(autoGenerate = false) val userId: String,
    val pinHash: String,
    val pinSalt: String,
    val failedLoginAttempts: Int = 0,
    val lockedUntil: Long?       = null
)

fun UserCredentialsEntity.toDomain() = UserCredentials(
    userId              = userId,
    pinHash             = pinHash,
    pinSalt             = pinSalt,
    failedLoginAttempts = failedLoginAttempts,
    lockedUntil         = lockedUntil
)

fun UserCredentials.toEntity() = UserCredentialsEntity(
    userId              = userId,
    pinHash             = pinHash,
    pinSalt             = pinSalt,
    failedLoginAttempts = failedLoginAttempts,
    lockedUntil         = lockedUntil
)
