package com.eternamente.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Re-schedules the daily reminder alarm after device reboot.
 *
 * AlarmManager alarms are cleared on power-off; this receiver fires on
 * [Intent.ACTION_BOOT_COMPLETED] and restores the alarm from saved DataStore preferences.
 *
 * Requires `android.permission.RECEIVE_BOOT_COMPLETED` in the Manifest.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler:           NotificationScheduler
    @Inject lateinit var preferencesRepository: UserPreferencesRepository

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        Timber.d("BootReceiver: device rebooted, restoring notification alarm")

        val pending = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val prefs = preferencesRepository.preferences.first()
                if (prefs.notificationsEnabled) {
                    scheduler.scheduleDaily(
                        hour     = prefs.notificationHour,
                        minute   = prefs.notificationMinute,
                        userName = prefs.notificationUserName
                    )
                    Timber.i("BootReceiver: alarm rescheduled at %02d:%02d".format(
                        prefs.notificationHour, prefs.notificationMinute
                    ))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
