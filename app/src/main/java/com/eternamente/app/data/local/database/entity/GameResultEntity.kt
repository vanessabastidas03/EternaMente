package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult

/**
 * `userId` está desnormalizado aquí (también existe en `cognitive_sessions`)
 * para permitir queries directas en `game_results` sin JOIN, que son
 * más eficientes para el pipeline de features del modelo ML.
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
        ),
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["userId"],
            onDelete      = ForeignKey.CASCADE
        )
    ]
)
data class GameResultEntity(
    @PrimaryKey(autoGenerate = false) val id: String,
    val sessionId: String,
    val userId: String,          // Desnormalizado para queries directas con índice
    val gameId: String,
    val domain: String,          // CognitiveDomain.name
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
