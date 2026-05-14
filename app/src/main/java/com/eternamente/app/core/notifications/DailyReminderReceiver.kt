package com.eternamente.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * BroadcastReceiver triggered by [NotificationScheduler]'s AlarmManager alarm.
 *
 * Responsibilities:
 * 1. Post the daily session-reminder notification via [EternaNotificationManager].
 * 2. Re-schedule itself for the next day (setExactAndAllowWhileIdle does NOT auto-repeat).
 */
@AndroidEntryPoint
class DailyReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationManager: EternaNotificationManager
    @Inject lateinit var scheduler:           NotificationScheduler

    companion object {
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_HOUR      = "alarm_hour"
        const val EXTRA_MINUTE    = "alarm_minute"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val userName = intent.getStringExtra(EXTRA_USER_NAME) ?: "amigo"
        val hour     = intent.getIntExtra(EXTRA_HOUR,   9)
        val minute   = intent.getIntExtra(EXTRA_MINUTE, 0)

        Timber.i("DailyReminderReceiver ▶ onReceive action=${intent.action}")
        Timber.i("DailyReminderReceiver   userName='$userName'  hour=$hour  minute=$minute")
        Timber.i("DailyReminderReceiver   currentTimeMs=${System.currentTimeMillis()}")

        notificationManager.showDailyReminder(userName = userName, currentStreak = 0)

        // Re-schedule for tomorrow (exact alarms don't repeat automatically)
        Timber.i("DailyReminderReceiver   re-scheduling for next day...")
        scheduler.scheduleDaily(hour, minute, userName)
        Timber.i("DailyReminderReceiver ✓ done")
    }
}
