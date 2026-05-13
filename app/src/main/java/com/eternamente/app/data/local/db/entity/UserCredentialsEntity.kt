package com.eternamente.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.UserCredentials

/**
 * Entidad Room que mapea la tabla `user_credentials`.
 *
 * La clave foránea a `users.id` garantiza integridad referencial:
 * al eliminar el usuario se eliminan sus credenciales en cascada.
 *
 * NUNCA se almacena el PIN en texto plano — solo [pinHash] y [pinSalt].
 */
@Entity(
    tableName = "user_credentials",
    foreignKeys = [
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["userId"],
            onDelete      = ForeignKey.CASCADE
        )
    ]
)
data class UserCredentialsEntity(
    @PrimaryKey val userId: String,
    val pinHash: String,            // PBKDF2-SHA256, Base64
    val pinSalt: String,            // 32 bytes aleatorios, Base64
    val failedLoginAttempts: Int    = 0,
    val lockedUntil: Long?          = null
)

internal fun UserCredentialsEntity.toDomain() = UserCredentials(
    userId               = userId,
    pinHash              = pinHash,
    pinSalt              = pinSalt,
    failedLoginAttempts  = failedLoginAttempts,
    lockedUntil          = lockedUntil
)

internal fun UserCredentials.toEntity() = UserCredentialsEntity(
    userId               = userId,
    pinHash              = pinHash,
    pinSalt              = pinSalt,
    failedLoginAttempts  = failedLoginAttempts,
    lockedUntil          = lockedUntil
)
