package com.eternamente.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.GamificationProfile

/** Room entity that maps to the `gamification_profiles` table. */
@Entity(tableName = "gamification_profiles")
data class GamificationProfileEntity(
    @PrimaryKey val userId: String,
    val totalPoints: Int,
    val currentStreak: Int,
    val maxStreak: Int,
    val lastSessionDate: String?,
    val badges: String            // Comma-separated Badge names; empty string = none earned
)

internal fun GamificationProfileEntity.toDomain() = GamificationProfile(
    userId          = userId,
    totalPoints     = totalPoints,
    currentStreak   = currentStreak,
    maxStreak       = maxStreak,
    lastSessionDate = lastSessionDate,
    badges          = badges
        .split(",")
        .filter { it.isNotBlank() }
        .map { Badge.valueOf(it) }
)

internal fun GamificationProfile.toEntity() = GamificationProfileEntity(
    userId          = userId,
    totalPoints     = totalPoints,
    currentStreak   = currentStreak,
    maxStreak       = maxStreak,
    lastSessionDate = lastSessionDate,
    badges          = badges.joinToString(",") { it.name }
)
