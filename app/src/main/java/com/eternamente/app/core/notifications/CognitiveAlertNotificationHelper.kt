package com.eternamente.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eternamente.app.MainActivity
import com.eternamente.app.R
import com.eternamente.app.domain.model.AlertLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts local notifications when the weekly cognitive analysis produces a [AlertLevel.WATCH]
 * or [AlertLevel.ALERT] result.
 *
 * The notification opens [MainActivity] so the user can view the full report.
 * Call [createChannel] once at app startup; [showCognitiveAlert] can then be called from
 * any thread or coroutine context.
 */
@Singleton
class CognitiveAlertNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID       = "eternamente_cognitive_alerts"
        private const val NOTIF_ID = 2_000
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Seguimiento cognitivo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Resultados del análisis cognitivo semanal"
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    /**
     * Posts a notification summarising the cognitive analysis result.
     * Only shown for [AlertLevel.WATCH] or [AlertLevel.ALERT]; no-ops for [AlertLevel.NORMAL].
     * On Android 13+ silently skips if POST_NOTIFICATIONS permission is not granted.
     */
    fun showCognitiveAlert(level: AlertLevel, explanation: String) {
        if (level == AlertLevel.NORMAL) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val title = when (level) {
            AlertLevel.WATCH -> "📊 Tu análisis semanal está listo"
            AlertLevel.ALERT -> "⚕️ Resultado de tu análisis semanal"
            AlertLevel.NORMAL -> return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(explanation)
            .setStyle(NotificationCompat.BigTextStyle().bigText(explanation))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }
}
