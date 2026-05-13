package com.eternamente.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.eternamente.app.core.notifications.BadgeNotificationHelper
import com.eternamente.app.core.notifications.CognitiveAlertNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point for EternaMente.
 *
 * Implements [Configuration.Provider] so WorkManager is initialised with
 * [HiltWorkerFactory], which enables `@HiltWorker` / `@AssistedInject` in workers.
 *
 * **Important:** The default WorkManager auto-initializer must be disabled in
 * `AndroidManifest.xml` (via `tools:node="remove"` on WorkManagerInitializer)
 * so WorkManager does not start before Hilt has finished injecting [workerFactory].
 */
@HiltAndroidApp
class EternaApp : Application(), Configuration.Provider {

    @Inject lateinit var badgeNotificationHelper: BadgeNotificationHelper
    @Inject lateinit var cognitiveAlertHelper:    CognitiveAlertNotificationHelper
    @Inject lateinit var workerFactory:           HiltWorkerFactory

    // WorkManager reads this on first getInstance() call to create the singleton.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initLogging()
        badgeNotificationHelper.createChannel()
        cognitiveAlertHelper.createChannel()
    }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
