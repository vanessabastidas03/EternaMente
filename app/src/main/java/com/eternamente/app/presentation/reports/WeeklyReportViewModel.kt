package com.eternamente.app.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.usecase.AnalyzeCognitivePatternUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class WeeklyReportState(
    val isLoading:    Boolean       = true,
    val isAnalyzing:  Boolean       = false,
    val prediction:   MlPrediction? = null,
    val errorMessage: String?       = null,
    val lastRunLabel: String?       = null
)

@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    private val analyzeUseCase:     AnalyzeCognitivePatternUseCase,
    private val mlRepository:       MlRepository,
    private val userPreferences:    UserPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyReportState())
    val state: StateFlow<WeeklyReportState> = _state.asStateFlow()

    init { loadLatestPrediction() }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun runManualAnalysis() {
        viewModelScope.launch {
            val userId = userPreferences.getCurrentUserId() ?: return@launch
            _state.update { it.copy(isAnalyzing = true, errorMessage = null) }

            analyzeUseCase(userId)
                .let { result ->
                    when {
                        result.isSuccess -> {
                            val prediction = (result as com.eternamente.app.core.Result.Success).data
                            _state.update {
                                it.copy(
                                    isAnalyzing  = false,
                                    prediction   = prediction,
                                    lastRunLabel = formatDate(prediction.predictionDate),
                                    errorMessage = null
                                )
                            }
                        }
                        else -> {
                            val msg = (result as? com.eternamente.app.core.Result.Error)
                                ?.exception?.message
                                ?: "No se pudo completar el análisis"
                            _state.update { it.copy(isAnalyzing = false, errorMessage = msg) }
                        }
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun loadLatestPrediction() {
        viewModelScope.launch {
            val userId = userPreferences.getCurrentUserId()
            if (userId == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            mlRepository.getLatestPrediction(userId).let { result ->
                val prediction = (result as? com.eternamente.app.core.Result.Success)?.data
                _state.update {
                    it.copy(
                        isLoading    = false,
                        prediction   = prediction,
                        lastRunLabel = prediction?.let { p -> formatDate(p.predictionDate) }
                    )
                }
            }
        }
    }

    private fun formatDate(epochMs: Long): String {
        val formatter = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
        return formatter.format(Instant.ofEpochMilli(epochMs))
    }
}
