package com.eternamente.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry point for EternaMente.
 *
 * Annotated with [@HiltAndroidApp] to trigger Hilt's code generation and
 * initialise the top-level [SingletonComponent].
 *
 * Responsibilities:
 * - Bootstrap Hilt dependency injection.
 * - Plant Timber logging tree in debug builds only (no-op in release).
 */
@HiltAndroidApp
class EternaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initLogging()
    }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // In release, Timber calls are silently discarded — no tree is planted.
    }
}
