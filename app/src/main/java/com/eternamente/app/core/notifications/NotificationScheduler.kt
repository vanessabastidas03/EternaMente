package com.eternamente.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules (and cancels) the daily session-reminder alarm using [AlarmManager].
 *
 * **Android 12+ (S):** If the user has not granted [SCHEDULE_EXACT_ALARM] permission,
 * the scheduler transparently falls back to [AlarmManager.setAndAllowWhileIdle],
 * which fires within a few minutes of the requested time but is not guaranteed exact.
 * Call [canScheduleExactAlarms] to check, and [openExactAlarmSettings] to guide
 * the user to the system screen if exactness is required.
 *
 * **Repeating:** [AlarmManager.setExactAndAllowWhileIdle] does NOT auto-repeat.
 * [DailyReminderReceiver] re-schedules itself for the next day after firing.
 * [BootReceiver] re-schedules after device reboot.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val ALARM_REQUEST_CODE = 1_001
        private const val TAG = "NotificationScheduler"
    }

    /**
     * Schedules a daily alarm that fires at [hour]:[minute] (24 h clock).
     * If the time has already passed today, the alarm fires tomorrow.
     */
    fun scheduleDaily(hour: Int, minute: Int, userName: String) {
        val alarmManager  = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(hour, minute, userName)
        val triggerMs     = nextAlarmMillis(hour, minute)
        val delayMin      = (triggerMs - System.currentTimeMillis()) / 60_000

        Timber.i("$TAG ▶ scheduleDaily hour=$hour min=$minute user='$userName'")
        Timber.i("$TAG   triggerAtMillis=$triggerMs  delayMin=$delayMin  requestCode=$ALARM_REQUEST_CODE")
        Timber.i("$TAG   pendingIntent=$pendingIntent  API=${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val canExact = alarmManager.canScheduleExactAlarms()
            Timber.i("$TAG   canScheduleExactAlarms=$canExact")
            if (!canExact) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
                Timber.w("$TAG   ⚠ INEXACT alarm set (no SCHEDULE_EXACT_ALARM permission)")
                return
            }
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        Timber.i("$TAG   ✓ EXACT alarm set — fires in $delayMin min")
    }

    /** Cancels any previously scheduled daily alarm. */
    fun cancelDaily() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Timber.d("$TAG: daily alarm cancelled")
    }

    /** `true` if the app can schedule exact alarms (always true below API 31). */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return am.canScheduleExactAlarms()
        }
        return true
    }

    /** Opens the system settings screen where the user can grant SCHEDULE_EXACT_ALARM. */
    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data  = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildPendingIntent(hour: Int, minute: Int, userName: String): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            putExtra(DailyReminderReceiver.EXTRA_USER_NAME, userName)
            putExtra(DailyReminderReceiver.EXTRA_HOUR,      hour)
            putExtra(DailyReminderReceiver.EXTRA_MINUTE,    minute)
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextAlarmMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE,      minute)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
