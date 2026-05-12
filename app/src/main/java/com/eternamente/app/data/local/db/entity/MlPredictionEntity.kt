package com.eternamente.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.MlPrediction

/** Room entity that maps to the `ml_predictions` table. */
@Entity(tableName = "ml_predictions")
data class MlPredictionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val predictionDate: Long,
    val riskScore: Float,
    val alertLevel: String,       // AlertLevel.name
    val domainsFlagged: String,   // Comma-separated CognitiveDomain names; empty string = none
    val explanation: String
)

internal fun MlPredictionEntity.toDomain() = MlPrediction(
    id             = id,
    userId         = userId,
    predictionDate = predictionDate,
    riskScore      = riskScore,
    alertLevel     = AlertLevel.valueOf(alertLevel),
    domainsFlagged = domainsFlagged
        .split(",")
        .filter { it.isNotBlank() }
        .map { CognitiveDomain.valueOf(it) },
    explanation    = explanation
)

internal fun MlPrediction.toEntity() = MlPredictionEntity(
    id             = id,
    userId         = userId,
    predictionDate = predictionDate,
    riskScore      = riskScore,
    alertLevel     = alertLevel.name,
    domainsFlagged = domainsFlagged.joinToString(",") { it.name },
    explanation    = explanation
)
