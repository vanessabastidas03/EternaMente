package com.eternamente.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eternamente.app.R
import com.eternamente.app.domain.model.Badge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows local push notifications when the user unlocks a new [Badge].
 *
 * The notification channel [CHANNEL_ID] must be created before posting —
 * call [createChannel] once at app startup from [Application.onCreate].
 *
 * On Android 13+ (API 33) the user must grant POST_NOTIFICATIONS permission.
 * If not granted, [showBadgeUnlocked] silently no-ops.
 */
@Singleton
class BadgeNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Logros y medallas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones al desbloquear nuevas medallas"
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    /**
     * Posts an immediate notification celebrating the unlocked [badge].
     * Safe to call from any coroutine context; notification is posted on the calling thread.
     */
    fun showBadgeUnlocked(badge: Badge) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🏆 ¡Nuevo logro desbloqueado!")
            .setContentText("${badge.displayName} — ${badge.description}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${badge.displayName}\n${badge.description}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_BASE + badge.ordinal, notification)
    }

    companion object {
        const val CHANNEL_ID    = "eternamente_badges"
        private const val NOTIF_ID_BASE = 1_000
    }
}
