package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.User

@Entity(
    tableName = "users",
    indices   = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = false) val id: String,
    val email: String,
    val name: String,
    val age: Int,
    val educationYears: Int,
    val gender: String,
    val createdAt: Long,
    val consentGivenAt: Long?
)

fun UserEntity.toDomain() = User(
    id             = id,
    email          = email,
    name           = name,
    age            = age,
    educationYears = educationYears,
    gender         = gender,
    createdAt      = createdAt,
    consentGivenAt = consentGivenAt
)

fun User.toEntity() = UserEntity(
    id             = id,
    email          = email,
    name           = name,
    age            = age,
    educationYears = educationYears,
    gender         = gender,
    createdAt      = createdAt,
    consentGivenAt = consentGivenAt
)
