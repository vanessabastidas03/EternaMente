package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.MlPrediction

@Entity(
    tableName   = "ml_predictions",
    foreignKeys = [
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["userId"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices     = [Index("userId"), Index("predictionDate")]
)
data class MlPredictionEntity(
    @PrimaryKey(autoGenerate = false) val id: String,
    val userId: String,
    val predictionDate: Long,
    val riskScore: Float,
    val alertLevel: String,
    val domainsFlagged: String,   // List<CognitiveDomain> → "MEMORY,ATTENTION" (via Converters)
    val explanation: String
)

fun MlPredictionEntity.toDomain() = MlPrediction(
    id             = id,
    userId         = userId,
    predictionDate = predictionDate,
    riskScore      = riskScore,
    alertLevel     = AlertLevel.valueOf(alertLevel),
    domainsFlagged = domainsFlagged.split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { CognitiveDomain.valueOf(it) }.getOrNull() },
    explanation    = explanation
)

fun MlPrediction.toEntity() = MlPredictionEntity(
    id             = id,
    userId         = userId,
    predictionDate = predictionDate,
    riskScore      = riskScore,
    alertLevel     = alertLevel.name,
    domainsFlagged = domainsFlagged.joinToString(",") { it.name },
    explanation    = explanation
)
