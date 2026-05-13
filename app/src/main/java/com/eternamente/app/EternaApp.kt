package com.eternamente.app

import android.app.Application
import com.eternamente.app.core.notifications.BadgeNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class EternaApp : Application() {

    @Inject lateinit var badgeNotificationHelper: BadgeNotificationHelper

    override fun onCreate() {
        super.onCreate()
        initLogging()
        badgeNotificationHelper.createChannel()
    }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
