package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.UserSettings

@Entity(
    tableName   = "user_settings",
    foreignKeys = [
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["userId"],
            onDelete      = ForeignKey.CASCADE
        )
    ]
)
data class SettingsEntity(
    @PrimaryKey(autoGenerate = false) val userId: String,
    val notificationsEnabled: Boolean   = true,
    val notificationHour: Int           = 9,
    val notificationMinute: Int         = 0,
    val sessionFrequencyPerWeek: Int    = 5,
    val language: String                = "es"
)

fun SettingsEntity.toDomain() = UserSettings(
    userId                   = userId,
    notificationsEnabled     = notificationsEnabled,
    notificationHour         = notificationHour,
    notificationMinute       = notificationMinute,
    sessionFrequencyPerWeek  = sessionFrequencyPerWeek,
    language                 = language
)

fun UserSettings.toEntity() = SettingsEntity(
    userId                   = userId,
    notificationsEnabled     = notificationsEnabled,
    notificationHour         = notificationHour,
    notificationMinute       = notificationMinute,
    sessionFrequencyPerWeek  = sessionFrequencyPerWeek,
    language                 = language
)
