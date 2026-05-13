package com.eternamente.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.eternamente.app.domain.model.CognitiveBaseline

@Entity(
    tableName   = "cognitive_baselines",
    foreignKeys = [
        ForeignKey(
            entity        = UserEntity::class,
            parentColumns = ["id"],
            childColumns  = ["userId"],
            onDelete      = ForeignKey.CASCADE
        )
    ]
)
data class BaselineEntity(
    @PrimaryKey(autoGenerate = false) val userId: String,
    val memoryScore: Float,
    val attentionScore: Float,
    val executiveScore: Float,
    val languageScore: Float,
    val orientationScore: Float,
    val overallScore: Float,
    val calculatedAt: Long
)

fun BaselineEntity.toDomain() = CognitiveBaseline(
    userId           = userId,
    memoryScore      = memoryScore,
    attentionScore   = attentionScore,
    executiveScore   = executiveScore,
    languageScore    = languageScore,
    orientationScore = orientationScore,
    overallScore     = overallScore,
    calculatedAt     = calculatedAt
)

fun CognitiveBaseline.toEntity() = BaselineEntity(
    userId           = userId,
    memoryScore      = memoryScore,
    attentionScore   = attentionScore,
    executiveScore   = executiveScore,
    languageScore    = languageScore,
    orientationScore = orientationScore,
    overallScore     = overallScore,
    calculatedAt     = calculatedAt
)
