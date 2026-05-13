package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.SessionType

@Entity(
    tableName    = "cognitive_sessions",
    foreignKeys  = [
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["userId"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices      = [Index("userId"), Index("sessionDate")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = false) val id: String,
    val userId: String,
    val sessionDate: Long,
    val durationSeconds: Int?,
    val type: String,       // SessionType.name — convertido por Converters
    val completed: Boolean
)

fun SessionEntity.toDomain() = CognitiveSession(
    id              = id,
    userId          = userId,
    sessionDate     = sessionDate,
    durationSeconds = durationSeconds,
    type            = SessionType.valueOf(type),
    completed       = completed
)

fun CognitiveSession.toEntity() = SessionEntity(
    id              = id,
    userId          = userId,
    sessionDate     = sessionDate,
    durationSeconds = durationSeconds,
    type            = type.name,
    completed       = completed
)
