package com.eternamente.app.core.worker

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val TAG = "WorkManagerSetup"

/**
 * Schedules (or re-uses an existing) weekly cognitive analysis job for [userId].
 *
 * Uses [ExistingPeriodicWorkPolicy.KEEP] so calling this multiple times
 * (e.g. on every app launch) does not reset the schedule or duplicate work.
 *
 * **Constraints:**
 * - Battery not low — avoids draining a nearly-empty battery.
 * - Not roaming — keeps the work on a home/WiFi network; analysis is local
 *   but notifications may need network. Cancel and reschedule with
 *   [cancelWeeklyCognitiveAnalysis] + this function if the policy should change.
 *
 * **Retry:** Exponential back-off starting at 15 min (capped by WorkManager at ~5 h).
 * The worker itself limits total attempts to [CognitiveAnalysisWorker.MAX_ATTEMPTS].
 *
 * @param userId UUID of the authenticated user — included in the unique work name
 *   so different users on the same device each get their own periodic request.
 */
fun Application.scheduleWeeklyCognitiveAnalysis(userId: String) {
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiredNetworkType(NetworkType.NOT_ROAMING)
        .build()

    val request = PeriodicWorkRequestBuilder<CognitiveAnalysisWorker>(7, TimeUnit.DAYS)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
        .setInputData(workDataOf(CognitiveAnalysisWorker.KEY_USER_ID to userId))
        .build()

    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        uniqueWorkName(userId),
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
    Timber.i("$TAG: scheduled weekly analysis for user=$userId")
}

/**
 * Cancels any scheduled weekly analysis for [userId].
 * Call on logout to prevent the worker from running after the session ends.
 */
fun Application.cancelWeeklyCognitiveAnalysis(userId: String) {
    WorkManager.getInstance(this).cancelUniqueWork(uniqueWorkName(userId))
    Timber.i("$TAG: cancelled weekly analysis for user=$userId")
}

/**
 * Enqueues a one-time immediate analysis request (does not replace the weekly schedule).
 * Used by "Analizar ahora" from the Reports screen when the user wants an on-demand run.
 */
fun Application.runImmediateCognitiveAnalysis(userId: String) {
    val request = androidx.work.OneTimeWorkRequestBuilder<CognitiveAnalysisWorker>()
        .setInputData(workDataOf(CognitiveAnalysisWorker.KEY_USER_ID to userId))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(this)
        .beginUniqueWork(
            "cognitive_analysis_immediate_$userId",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
        .enqueue()

    Timber.i("$TAG: enqueued immediate analysis for user=$userId")
}

private fun uniqueWorkName(userId: String) = "cognitive_analysis_$userId"
