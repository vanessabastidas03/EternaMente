package com.eternamente.app.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.core.Result
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.ml.AlertGenerator
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.repository.FeatureQueryRepository
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.usecase.AnalyzeCognitivePatternUseCase
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class ReportState(
    val isLoading: Boolean = true,
    val weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val weekEnd: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(6),
    val weeklyDomainScores: Map<CognitiveDomain, Float> = emptyMap(),
    val previousWeekDomainScores: Map<CognitiveDomain, Float> = emptyMap(),
    val baselineScore: Float = 0f,
    val daysCompletedThisWeek: Int = 0,
    val weeklyTrend: List<Float> = List(8) { 0f },
    val completedDaysThisMonth: Set<Int> = emptySet(),
    val latestPrediction: MlPrediction? = null,
    val aiMessage: String = "",
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val gameResultRepository: GameResultRepository,
    private val sessionRepository: SessionRepository,
    private val mlRepository: MlRepository,
    private val featureQueryRepository: FeatureQueryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val analyzeUseCase: AnalyzeCognitivePatternUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val userId = userPreferencesRepository.getCurrentUserId()
            if (userId == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            val zone = ZoneId.systemDefault()
            val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekEnd = weekStart.plusDays(6)

            val weekFromMs = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val weekToMs = weekEnd.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

            val prevWeekEnd = weekStart.minusDays(1)
            val prevWeekStart = prevWeekEnd.minusDays(6)
            val prevFromMs = prevWeekStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val prevToMs = prevWeekEnd.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

            withContext(Dispatchers.IO) {
                val currentDomainScoresDeferred = async {
                    (gameResultRepository.getAveragesByDomainInRange(userId, weekFromMs, weekToMs) as? Result.Success)?.data
                        ?: emptyMap()
                }
                val prevDomainScoresDeferred = async {
                    (gameResultRepository.getAveragesByDomainInRange(userId, prevFromMs, prevToMs) as? Result.Success)?.data
                        ?: emptyMap()
                }
                val daysCompletedDeferred = async {
                    (sessionRepository.countCompletedSessions(userId, weekFromMs, weekToMs) as? Result.Success)?.data
                        ?: 0
                }
                val predictionDeferred = async {
                    (mlRepository.getLatestPrediction(userId) as? Result.Success)?.data
                }
                val baselineDeferred = async {
                    val scores = featureQueryRepository.earliestScores(userId, 15)
                    if (scores.isEmpty()) 0f else scores.sum() / scores.size
                }
                val weeklyTrendDeferred = async {
                    (7 downTo 0).map { weeksAgo ->
                        val end = weekStart.minusWeeks(weeksAgo.toLong()).plusDays(6)
                        val start = end.minusDays(6)
                        val fMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
                        val tMs = end.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                        (gameResultRepository.getOverallAverageInRange(userId, fMs, tMs) as? Result.Success)?.data
                            ?: 0f
                    }
                }
                val sessionDatesDeferred = async {
                    (sessionRepository.getAllSessionDates(userId) as? Result.Success)?.data
                        ?: emptyList()
                }

                val currentDomainScores = currentDomainScoresDeferred.await()
                val prevDomainScores = prevDomainScoresDeferred.await()
                val daysCompleted = daysCompletedDeferred.await()
                val prediction = predictionDeferred.await()
                val baseline = baselineDeferred.await()
                val weeklyTrend = weeklyTrendDeferred.await()
                val sessionDates = sessionDatesDeferred.await()

                val currentYearMonth = YearMonth.now()
                val completedDaysThisMonth = sessionDates
                    .map { epochMs ->
                        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
                    }
                    .filter { date -> YearMonth.from(date) == currentYearMonth }
                    .map { it.dayOfMonth }
                    .toSet()

                val aiMessage = prediction?.let {
                    AlertGenerator.generate(it.alertLevel, it.domainsFlagged)
                } ?: ""

                _state.update {
                    it.copy(
                        isLoading = false,
                        weekStart = weekStart,
                        weekEnd = weekEnd,
                        weeklyDomainScores = currentDomainScores,
                        previousWeekDomainScores = prevDomainScores,
                        baselineScore = baseline,
                        daysCompletedThisWeek = daysCompleted,
                        weeklyTrend = weeklyTrend,
                        completedDaysThisMonth = completedDaysThisMonth,
                        latestPrediction = prediction,
                        aiMessage = aiMessage
                    )
                }
            }
        }
    }

    fun runManualAnalysis() {
        viewModelScope.launch {
            val userId = userPreferencesRepository.getCurrentUserId() ?: return@launch
            _state.update { it.copy(isAnalyzing = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) { analyzeUseCase(userId) }
            when (result) {
                is Result.Success -> {
                    val prediction = result.data
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            latestPrediction = prediction,
                            aiMessage = AlertGenerator.generate(prediction.alertLevel, prediction.domainsFlagged)
                        )
                    }
                }
                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            errorMessage = result.exception.message ?: "No se pudo completar el análisis"
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun generateWeeklyChartData(): List<BarEntry> {
        val scores = _state.value.weeklyDomainScores
        val domains = listOf(
            CognitiveDomain.MEMORY,
            CognitiveDomain.ATTENTION,
            CognitiveDomain.EXECUTIVE,
            CognitiveDomain.LANGUAGE,
            CognitiveDomain.ORIENTATION
        )
        return domains.mapIndexed { index, domain ->
            BarEntry(index.toFloat(), scores[domain] ?: 0f)
        }
    }

    fun generateMonthlyChartData(): List<Entry> {
        return _state.value.weeklyTrend
            .mapIndexed { index, score -> index to score }
            .filter { (_, score) -> score > 0f }
            .map { (index, score) -> Entry(index.toFloat(), score) }
    }
}
