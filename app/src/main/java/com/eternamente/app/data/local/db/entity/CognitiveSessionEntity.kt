package com.eternamente.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.SessionType

/** Room entity that maps to the `cognitive_sessions` table. */
@Entity(tableName = "cognitive_sessions")
data class CognitiveSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sessionDate: Long,
    val durationSeconds: Int?,
    val type: String,           // Stored as SessionType.name
    val completed: Boolean
)

internal fun CognitiveSessionEntity.toDomain() = CognitiveSession(
    id              = id,
    userId          = userId,
    sessionDate     = sessionDate,
    durationSeconds = durationSeconds,
    type            = SessionType.valueOf(type),
    completed       = completed
)

internal fun CognitiveSession.toEntity() = CognitiveSessionEntity(
    id              = id,
    userId          = userId,
    sessionDate     = sessionDate,
    durationSeconds = durationSeconds,
    type            = type.name,
    completed       = completed
)
