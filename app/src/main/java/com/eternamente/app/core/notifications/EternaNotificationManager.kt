package com.eternamente.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eternamente.app.MainActivity
import com.eternamente.app.R
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.Badge
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central notification manager for EternaMente.
 *
 * Owns all three notification channels and exposes typed methods for each
 * notification category. Call [createAllChannels] once in [Application.onCreate].
 *
 * Channels:
 * - [CHANNEL_DAILY]  — "Sesión diaria"   (MEDIUM, no intrusive sound)
 * - [CHANNEL_ALERT]  — "Alerta cognitiva" (HIGH, soft default sound)
 * - [CHANNEL_BADGE]  — "Logros"           (LOW, silent)
 */
@Singleton
class EternaNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_DAILY = "daily_reminder"
        const val CHANNEL_ALERT = "cognitive_alert"
        const val CHANNEL_BADGE = "achievement"

        // Legacy channels kept for backward compatibility with existing helpers
        const val CHANNEL_BADGES_LEGACY    = "eternamente_badges"
        const val CHANNEL_COGNITIVE_LEGACY = "eternamente_cognitive_alerts"

        // Notification IDs
        private const val ID_DAILY     = 100
        private const val ID_ALERT     = 200
        private const val ID_BADGE_BASE = 300

        // Intent extras for deep linking
        const val EXTRA_DESTINATION  = "notif_destination"
        const val DEST_GAME_CATALOG  = "game_catalog"
        const val DEST_ALERT_DETAIL  = "alert_detail"
        const val EXTRA_ALERT_ID     = "alert_id"
    }

    // ── Channel creation ──────────────────────────────────────────────────────

    fun createAllChannels() {
        Timber.i("EternaNotifMgr: createAllChannels() called")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Daily reminder — MEDIUM importance, no intrusive sound
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_DAILY, "Sesión diaria", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Recordatorio diario para completar tu sesión cognitiva"
            setSound(null, null)
            enableVibration(false)
        })

        // Cognitive alert — HIGH importance, default soft notification sound
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ALERT, "Alerta cognitiva", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Resultados del análisis cognitivo semanal"
            setSound(
                Uri.parse("content://settings/system/notification_sound"),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        })

        // Achievement — LOW importance, silent
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_BADGE, "Logros", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificaciones de logros y medallas desbloqueadas"
            setSound(null, null)
            enableVibration(false)
        })

        // Legacy channels for backward compat
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_BADGES_LEGACY, "Logros y medallas", NotificationManager.IMPORTANCE_HIGH
        ))
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_COGNITIVE_LEGACY, "Seguimiento cognitivo", NotificationManager.IMPORTANCE_HIGH
        ))
    }

    // ── Notification methods ──────────────────────────────────────────────────

    /**
     * Posts the daily session reminder.
     * Deep link: opens [GameCatalog] on tap.
     */
    fun showDailyReminder(userName: String, currentStreak: Int) {
        Timber.i("EternaNotifMgr: showDailyReminder userName='$userName' streak=$currentStreak")
        val canPostResult = canPost()
        Timber.i("EternaNotifMgr: canPost()=$canPostResult  API=${Build.VERSION.SDK_INT}")
        if (!canPostResult) {
            Timber.w("EternaNotifMgr: ✗ POST_NOTIFICATIONS not granted — notification suppressed")
            return
        }

        val body = if (currentStreak > 0)
            "Mantén tu racha de $currentStreak días seguidos 🔥"
        else
            "Tu sesión de hoy te está esperando"

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("¡$userName, es hora de tu sesión de hoy! 🧠")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapIntent(DEST_GAME_CATALOG))
            .setAutoCancel(true)
            .build()

        Timber.i("EternaNotifMgr: calling notify(ID=$ID_DAILY, channel=$CHANNEL_DAILY)")
        NotificationManagerCompat.from(context).notify(ID_DAILY, notification)
        Timber.i("EternaNotifMgr: ✓ notify() called")
    }

    /**
     * Posts a cognitive-analysis alert.
     * Deep link: opens [AlertDetail] (or [Dashboard]) on tap.
     */
    fun showCognitiveAlert(alertLevel: AlertLevel, message: String, alertId: String? = null) {
        if (!canPost()) return
        val dest = if (alertId != null) DEST_ALERT_DETAIL else DEST_GAME_CATALOG
        val intent = tapIntent(dest, alertId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("EternaMente tiene un mensaje para ti")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(ID_ALERT, notification)
    }

    /**
     * Posts a badge-unlocked celebration.
     */
    fun showAchievement(badge: Badge) {
        Timber.i("EternaNotifMgr: showAchievement badge=${badge.name}")
        if (!canPost()) {
            Timber.w("EternaNotifMgr: ✗ POST_NOTIFICATIONS not granted — badge suppressed")
            return
        }
        val notifId = ID_BADGE_BASE + badge.ordinal
        val notification = NotificationCompat.Builder(context, CHANNEL_BADGE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🏆 ¡Nuevo logro desbloqueado!")
            .setContentText("${badge.displayName} — ${badge.description}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${badge.displayName}\n${badge.description}"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        Timber.i("EternaNotifMgr: calling notify(ID=$notifId, channel=$CHANNEL_BADGE)")
        NotificationManagerCompat.from(context).notify(notifId, notification)
        Timber.i("EternaNotifMgr: ✓ badge notify() called")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Timber.d("EternaNotifMgr: canPost() API33+ POST_NOTIFICATIONS granted=$granted")
            return granted
        }
        Timber.d("EternaNotifMgr: canPost() API<33 → always true")
        return true
    }

    private fun tapIntent(destination: String, extra: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DESTINATION, destination)
            extra?.let { putExtra(EXTRA_ALERT_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            destination.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
