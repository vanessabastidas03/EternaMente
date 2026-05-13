package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.SessionType

/**
 * Sesión cognitiva en Room.
 *
 * **Sin FK en userId** (igual que game_results) para evitar
 * `SQLiteConstraintException` cuando la tabla `users` está vacía
 * (ej. después de `fallbackToDestructiveMigration` durante desarrollo).
 * La integridad se mantiene por convención en la capa de repositorio.
 */
@Entity(
    tableName = "cognitive_sessions",
    indices   = [Index("userId"), Index("sessionDate")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = false) val id: String,
    val userId: String,
    val sessionDate: Long,
    val durationSeconds: Int?,
    val type: String,
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
