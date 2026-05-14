package com.eternamente.app.presentation.reports

import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.core.Result
import com.eternamente.app.core.pdf.PdfReportData
import com.eternamente.app.core.pdf.PdfReportGenerator
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class PdfExportUiState {
    /** Initial state — shows expected filename and action buttons. */
    data class Idle(val expectedFileName: String) : PdfExportUiState()
    object Generating : PdfExportUiState()
    data class Success(val file: File) : PdfExportUiState()
    data class Error(val message: String) : PdfExportUiState()
}

@HiltViewModel
class PdfExportViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pdfGenerator:       PdfReportGenerator,
    private val userRepository:     UserRepository,
    private val userPreferences:    UserPreferencesRepository,
    private val sessionRepository:  SessionRepository,
    private val gameResultRepo:     GameResultRepository,
    private val mlRepository:       MlRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfExportUiState>(
        PdfExportUiState.Idle(pdfGenerator.expectedFileName())
    )
    val uiState: StateFlow<PdfExportUiState> = _uiState.asStateFlow()

    // One-shot share intent for the screen to launch
    private val _shareIntent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val shareIntent: SharedFlow<Intent> = _shareIntent.asSharedFlow()

    fun generateAndSave(reportState: ReportState) {
        generate(reportState, shareAfter = false)
    }

    fun generateAndShare(reportState: ReportState) {
        generate(reportState, shareAfter = true)
    }

    fun resetToIdle() {
        _uiState.value = PdfExportUiState.Idle(pdfGenerator.expectedFileName())
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun generate(reportState: ReportState, shareAfter: Boolean) {
        viewModelScope.launch {
            _uiState.value = PdfExportUiState.Generating
            try {
                val data = buildReportData(reportState) ?: run {
                    _uiState.value = PdfExportUiState.Error("No hay sesión activa")
                    return@launch
                }
                val file = pdfGenerator.generate(data)
                _uiState.value = PdfExportUiState.Success(file)
                if (shareAfter) launchShareIntent(file)
            } catch (e: Exception) {
                _uiState.value = PdfExportUiState.Error(e.message ?: "Error al generar el PDF")
            }
        }
    }

    private suspend fun buildReportData(state: ReportState): PdfReportData? {
        val userId = userPreferences.getCurrentUserId() ?: return null
        val user   = withContext(Dispatchers.IO) {
            (userRepository.getUserById(userId) as? Result.Success)?.data ?: return@withContext null
        } ?: return null

        // Days completed in last 4 weeks
        val fourWeeksAgo = System.currentTimeMillis() - 28L * 86_400_000
        val daysIn4W = withContext(Dispatchers.IO) {
            (sessionRepository.countCompletedSessions(userId, fourWeeksAgo, System.currentTimeMillis()) as? Result.Success)?.data ?: 0
        }

        return PdfReportData(
            userName           = user.name,
            userAge            = user.age,
            weekStart          = state.weekStart,
            weekEnd            = state.weekEnd,
            weeklyDomainScores = state.weeklyDomainScores,
            baselineScore      = state.baselineScore,
            daysCompletedIn4W  = daysIn4W,
            weeklyTrend        = state.weeklyTrend,
            latestPrediction   = state.latestPrediction,
            aiMessage          = state.aiMessage
        )
    }

    private fun launchShareIntent(file: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val intent = ShareCompat.IntentBuilder(appContext)
            .setType("application/pdf")
            .addStream(uri)
            .setChooserTitle("Compartir reporte cognitivo")
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        _shareIntent.tryEmit(intent)
    }
}
