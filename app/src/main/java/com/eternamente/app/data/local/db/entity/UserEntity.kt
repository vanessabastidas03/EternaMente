package com.eternamente.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.User

/** Room entity that maps to the `users` table. */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val age: Int,
    val educationYears: Int,
    val gender: String,
    val createdAt: Long,
    val consentGivenAt: Long?
)

internal fun UserEntity.toDomain() = User(
    id             = id,
    name           = name,
    age            = age,
    educationYears = educationYears,
    gender         = gender,
    createdAt      = createdAt,
    consentGivenAt = consentGivenAt
)

internal fun User.toEntity() = UserEntity(
    id             = id,
    name           = name,
    age            = age,
    educationYears = educationYears,
    gender         = gender,
    createdAt      = createdAt,
    consentGivenAt = consentGivenAt
)
