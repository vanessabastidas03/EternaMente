package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult

/**
 * `userId` está desnormalizado aquí para queries directas en `game_results` sin JOIN.
 *
 * **Decisión de diseño:** el `userId` NO tiene FK enforcement para evitar
 * `SQLiteConstraintException` cuando se guarda un resultado cuyo `userId`
 * aún no existe en la tabla `users` (p. ej. en demos o tests de integración).
 * La integridad se mantiene por convención en la capa de repositorio.
 *
 * El `sessionId` SÍ tiene FK con CASCADE DELETE para que al eliminar una sesión
 * se eliminen automáticamente sus resultados.
 */
@Entity(
    tableName   = "game_results",
    indices     = [Index("sessionId"), Index("userId"), Index("domain")],
    foreignKeys = [
        ForeignKey(
            entity        = SessionEntity::class,
            parentColumns = ["id"],
            childColumns  = ["sessionId"],
            onDelete      = ForeignKey.CASCADE
            // userId: NO FK — see KDoc above
        )
    ]
)
data class GameResultEntity(
    @PrimaryKey(autoGenerate = false) val id: String,
    val sessionId: String,
    val userId: String,
    val gameId: String,
    val domain: String,
    val scoreRaw: Float,
    val scoreNormalized: Float,
    val reactionTimeMsAvg: Float,
    val reactionTimeMsP50: Float,
    val accuracyPct: Float,
    val errorsCount: Int,
    val difficultyLevel: Int
)

fun GameResultEntity.toDomain() = GameResult(
    id                = id,
    sessionId         = sessionId,
    gameId            = gameId,
    domain            = CognitiveDomain.valueOf(domain),
    scoreRaw          = scoreRaw,
    scoreNormalized   = scoreNormalized,
    reactionTimeMsAvg = reactionTimeMsAvg,
    reactionTimeMsP50 = reactionTimeMsP50,
    accuracyPct       = accuracyPct,
    errorsCount       = errorsCount,
    difficultyLevel   = difficultyLevel
)

fun GameResult.toEntity(userId: String) = GameResultEntity(
    id                = id,
    sessionId         = sessionId,
    userId            = userId,
    gameId            = gameId,
    domain            = domain.name,
    scoreRaw          = scoreRaw,
    scoreNormalized   = scoreNormalized,
    reactionTimeMsAvg = reactionTimeMsAvg,
    reactionTimeMsP50 = reactionTimeMsP50,
    accuracyPct       = accuracyPct,
    errorsCount       = errorsCount,
    difficultyLevel   = difficultyLevel
)
