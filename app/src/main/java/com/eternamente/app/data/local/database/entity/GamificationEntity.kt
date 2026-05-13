package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.GamificationProfile

@Entity(
    tableName   = "gamification",
    foreignKeys = [
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["userId"],
            onDelete      = ForeignKey.CASCADE
        )
    ]
)
data class GamificationEntity(
    @PrimaryKey(autoGenerate = false) val userId: String,
    val totalPoints: Int       = 0,
    val currentStreak: Int     = 0,
    val maxStreak: Int         = 0,
    val lastSessionDate: String? = null,   // ISO-8601 date (ej. "2024-03-15")
    val badges: String         = ""        // List<Badge> → "FIRST_STEP,WEEK_WARRIOR"
)

fun GamificationEntity.toDomain() = GamificationProfile(
    userId          = userId,
    totalPoints     = totalPoints,
    currentStreak   = currentStreak,
    maxStreak       = maxStreak,
    lastSessionDate = lastSessionDate,
    badges          = badges.split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { Badge.valueOf(it) }.getOrNull() }
)

fun GamificationProfile.toEntity() = GamificationEntity(
    userId          = userId,
    totalPoints     = totalPoints,
    currentStreak   = currentStreak,
    maxStreak       = maxStreak,
    lastSessionDate = lastSessionDate,
    badges          = badges.joinToString(",") { it.name }
)
