package com.eternamente.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eternamente.app.core.Result
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.repository.GamificationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @Inject lateinit var notificationManager:    EternaNotificationManager
    @Inject lateinit var scheduler:              NotificationScheduler
    @Inject lateinit var preferencesRepository:  UserPreferencesRepository
    @Inject lateinit var gamificationRepository: GamificationRepository

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

        // Fetch real streak — BroadcastReceiver runs on main thread; use runBlocking
        // (acceptable here: receiver is short-lived, I/O is local Room query)
        val streak = runBlocking {
            val userId = preferencesRepository.preferences.first().currentUserId
            if (userId != null) {
                when (val r = gamificationRepository.getProfile(userId)) {
                    is Result.Success -> r.data.currentStreak
                    is Result.Error   -> 0
                }
            } else 0
        }
        Timber.i("DailyReminderReceiver   streak=$streak")

        notificationManager.showDailyReminder(userName = userName, currentStreak = streak)

        // Re-schedule for tomorrow (exact alarms don't repeat automatically)
        Timber.i("DailyReminderReceiver   re-scheduling for next day...")
        scheduler.scheduleDaily(hour, minute, userName)
        Timber.i("DailyReminderReceiver ✓ done")
    }
}
