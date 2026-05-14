package com.eternamente.app.core.pdf

import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.MlPrediction
import java.time.LocalDate

data class PdfReportData(
    val userName:           String,
    val userAge:            Int,
    val weekStart:          LocalDate,
    val weekEnd:            LocalDate,
    val weeklyDomainScores: Map<CognitiveDomain, Float>,
    val baselineScore:      Float,
    val daysCompletedIn4W:  Int,
    val weeklyTrend:        List<Float>,          // 8 values oldest→newest
    val latestPrediction:   MlPrediction?,
    val aiMessage:          String,
    val generatedAt:        Long = System.currentTimeMillis()
)
