package com.eternamente.app.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eternamente.app.core.notifications.CognitiveAlertNotificationHelper
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.ml.CognitiveAnalyzer
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.core.Result as AppResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.UUID

/**
 * Background worker that runs the full cognitive analysis pipeline once per week.
 *
 * **Execution contract:**
 * 1. Reads the active userId from [inputData] (key: [KEY_USER_ID]).
 * 2. Verifies ≥ [MIN_SESSIONS_REQUIRED] completed sessions; skips silently if not.
 * 3. Delegates to [CognitiveAnalyzer.analyze] (feature extraction + Isolation Forest + TFLite).
 * 4. Persists the result via [MlRepository.savePrediction].
 * 5. Posts a local notification for [AlertLevel.WATCH] or [AlertLevel.ALERT] results.
 * 6. Returns [Result.success] on completion or graceful skip.
 *
 * **Retry policy:** Up to [MAX_ATTEMPTS] total attempts with exponential back-off
 * configured in [WorkManagerSetup]. After [MAX_ATTEMPTS], returns [Result.success]
 * so the periodic schedule is not cancelled.
 *
 * Injected via `@HiltWorker` / `@AssistedInject` — requires [HiltWorkerFactory]
 * configured in [EternaApp].
 */
@HiltWorker
class CognitiveAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cognitiveAnalyzer:    CognitiveAnalyzer,
    private val mlRepository:         MlRepository,
    private val sessionRepository:    SessionRepository,
    private val userPreferences:      UserPreferencesRepository,
    private val notificationHelper:   CognitiveAlertNotificationHelper
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_USER_ID = "userId"
        private const val MIN_SESSIONS_REQUIRED = 7
        private const val MAX_ATTEMPTS          = 3
        private const val TAG                   = "CognitiveAnalysisWorker"
    }

    override suspend fun doWork(): Result {
        // Graceful give-up after MAX_ATTEMPTS to preserve the periodic schedule.
        if (runAttemptCount >= MAX_ATTEMPTS) {
            Timber.w("$TAG: reached $MAX_ATTEMPTS attempts, skipping this week's run")
            return Result.success()
        }

        return try {
            val userId = resolveUserId() ?: run {
                Timber.w("$TAG: no active user, skipping")
                return Result.success()
            }

            // Guard: require minimum session history before running ML
            val sessionCount = when (val r = sessionRepository.countAllCompletedSessions(userId)) {
                is AppResult.Success -> r.data
                is AppResult.Error   -> 0
            }
            if (sessionCount < MIN_SESSIONS_REQUIRED) {
                Timber.d("$TAG: only $sessionCount sessions (need $MIN_SESSIONS_REQUIRED), skipping")
                return Result.success()
            }

            // Run the full analysis pipeline
            Timber.i("$TAG: starting analysis for user=$userId (attempt ${runAttemptCount + 1})")
            val analysis = cognitiveAnalyzer.analyze(userId)

            // Persist as MlPrediction
            val prediction = MlPrediction(
                id             = UUID.randomUUID().toString(),
                userId         = userId,
                predictionDate = analysis.analyzedAt,
                riskScore      = analysis.combinedRiskScore,
                alertLevel     = analysis.alertLevel,
                domainsFlagged = analysis.flaggedDomains,
                explanation    = analysis.explanation
            )
            mlRepository.savePrediction(prediction)
            Timber.i("$TAG: saved prediction — level=${analysis.alertLevel}, score=%.2f"
                .format(analysis.combinedRiskScore))

            // Notify user if level warrants attention
            if (analysis.alertLevel == AlertLevel.WATCH || analysis.alertLevel == AlertLevel.ALERT) {
                notificationHelper.showCognitiveAlert(analysis.alertLevel, analysis.explanation)
            }

            Result.success()

        } catch (e: Exception) {
            Timber.e("$TAG: error on attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS — ${e.message}")
            Result.retry()
        }
    }

    /** Resolves the userId from inputData, falling back to the DataStore active session. */
    private suspend fun resolveUserId(): String? =
        inputData.getString(KEY_USER_ID)
            ?: userPreferences.getCurrentUserId()
}
