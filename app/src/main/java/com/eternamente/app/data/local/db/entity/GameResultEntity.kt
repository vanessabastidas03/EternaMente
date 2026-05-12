package com.eternamente.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult

/** Room entity that maps to the `game_results` table. */
@Entity(tableName = "game_results")
data class GameResultEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val gameId: String,
    val domain: String,             // Stored as CognitiveDomain.name
    val scoreRaw: Float,
    val scoreNormalized: Float,
    val reactionTimeMsAvg: Float,
    val reactionTimeMsP50: Float,
    val accuracyPct: Float,
    val errorsCount: Int,
    val difficultyLevel: Int
)

internal fun GameResultEntity.toDomain() = GameResult(
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

internal fun GameResult.toEntity() = GameResultEntity(
    id                = id,
    sessionId         = sessionId,
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
