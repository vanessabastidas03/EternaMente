package com.eternamente.app.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eternamente.app.MainActivity
import com.eternamente.app.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

/**
 * Servicio Firebase Cloud Messaging de EternaMente.
 *
 * Responsabilidades:
 * - [onNewToken]: registra el token FCM y prepara estructura para sincronización futura.
 * - [onMessageReceived]: parsea el payload push y muestra la notificación usando los
 *   canales ya creados por [EternaNotificationManager] en `EternaApp.onCreate()`.
 *
 * **Política de canales:**
 * El payload FCM puede incluir `data.channel` para enrutar al canal correcto:
 * - `"daily_reminder"`   → CHANNEL_DAILY  (MEDIUM importance)
 * - `"cognitive_alert"`  → CHANNEL_ALERT  (HIGH importance)
 * - `"achievement"`      → CHANNEL_BADGE  (LOW importance)
 * Si no se especifica, se usa CHANNEL_DAILY como fallback.
 *
 * **Formato del payload FCM recomendado (Firebase Console / Admin SDK):**
 * ```json
 * {
 *   "to": "<device_token>",
 *   "notification": {
 *     "title": "Título",
 *     "body":  "Cuerpo del mensaje"
 *   },
 *   "data": {
 *     "channel": "daily_reminder",
 *     "destination": "game_catalog"
 *   }
 * }
 * ```
 */
class EternaMenteMessagingService : FirebaseMessagingService() {

    // ── onNewToken ────────────────────────────────────────────────────────────

    /**
     * Llamado por Firebase cuando se genera o rota el token FCM del dispositivo.
     *
     * En el MVP solo se registra el token con Timber (visible en Logcat con tag [TAG]).
     *
     * TODO (v1.5 — sincronización backend):
     *   1. Guardar token en DataStore o Room junto al userId activo.
     *   2. Enviar al servidor: POST /api/devices { userId, token, platform="android" }.
     *   3. Manejar rotación: Firebase invalida el token anterior y llama a este método.
     *   4. Si el usuario no está autenticado al recibir el token, encolar para enviar
     *      al próximo login exitoso.
     *
     * @param token Nuevo token FCM generado por Firebase. NO compartir en producción.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.i("$TAG ▶ onNewToken llamado")
        // Seguridad: el token FCM es equivalente a una credencial de entrega push.
        // Se loguea solo los primeros 8 caracteres para diagnóstico; el token completo
        // se escribe únicamente en el tag dedicado (filtra en Logcat: EternaFCM_Token).
        val preview = token.take(8) + "…"
        Timber.i("$TAG   FCM TOKEN: $preview  (filtrar por tag $TAG_TOKEN para ver completo)")
        Timber.tag(TAG_TOKEN).i(token)   // token completo — solo accesible con filtro explícito
        // TODO v1.5: persistir y sincronizar con backend
    }

    // ── onMessageReceived ─────────────────────────────────────────────────────

    /**
     * Llamado cuando llega un mensaje FCM con la app en primer o segundo plano.
     *
     * **Cuando la app está en background y el payload tiene `notification`:**
     * Firebase muestra la notificación de sistema automáticamente sin llamar a este método.
     * Para garantizar control total, se recomienda usar payloads solo con `data`.
     *
     * **Resolución de título y body:**
     * 1. Prioridad: `notification.title/body` del payload.
     * 2. Fallback: `data.title` / `data.body`.
     * 3. Si no hay body → se descarta la notificación para evitar bandejas vacías.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Timber.i("$TAG ▶ onMessageReceived")
        Timber.i("$TAG   from       = ${remoteMessage.from}")
        Timber.i("$TAG   messageId  = ${remoteMessage.messageId}")
        Timber.i("$TAG   data       = ${remoteMessage.data}")
        remoteMessage.notification?.let { notif ->
            Timber.i("$TAG   notif.title   = '${notif.title}'")
            Timber.i("$TAG   notif.body    = '${notif.body}'")
            Timber.i("$TAG   notif.channel = '${notif.channelId}'")
        }

        val title   = resolveTitle(remoteMessage)
        val body    = resolveBody(remoteMessage)
        val channel = resolveChannel(remoteMessage)

        if (body.isNullOrBlank()) {
            Timber.w("$TAG   ⚠ Payload sin body — notificación descartada")
            return
        }

        Timber.i("$TAG   → canal='$channel'  title='$title'  body='$body'")
        postNotification(
            title   = title ?: DEFAULT_TITLE,
            body    = body,
            channel = channel
        )
    }

    // ── Resolvers ─────────────────────────────────────────────────────────────

    private fun resolveTitle(msg: RemoteMessage): String? =
        msg.notification?.title?.takeIf { it.isNotBlank() }
            ?: msg.data[DATA_KEY_TITLE]?.takeIf { it.isNotBlank() }

    private fun resolveBody(msg: RemoteMessage): String? =
        msg.notification?.body?.takeIf { it.isNotBlank() }
            ?: msg.data[DATA_KEY_BODY]?.takeIf { it.isNotBlank() }

    /**
     * Determina el canal de notificación a partir del payload.
     * Valida contra los IDs de canal existentes; si el canal no es reconocido
     * usa [EternaNotificationManager.CHANNEL_DAILY] como fallback seguro.
     */
    private fun resolveChannel(msg: RemoteMessage): String {
        val requested = msg.notification?.channelId?.takeIf { it.isNotBlank() }
            ?: msg.data[DATA_KEY_CHANNEL]?.takeIf { it.isNotBlank() }
        return when (requested) {
            EternaNotificationManager.CHANNEL_DAILY,
            EternaNotificationManager.CHANNEL_ALERT,
            EternaNotificationManager.CHANNEL_BADGE -> requested
            else -> EternaNotificationManager.CHANNEL_DAILY
        }
    }

    // ── Notificación ──────────────────────────────────────────────────────────

    private fun postNotification(title: String, body: String, channel: String) {
        if (!canPost()) {
            Timber.w("$TAG   ⚠ POST_NOTIFICATIONS no concedido — notificación suprimida")
            return
        }

        val priority = when (channel) {
            EternaNotificationManager.CHANNEL_ALERT -> NotificationCompat.PRIORITY_HIGH
            EternaNotificationManager.CHANNEL_BADGE -> NotificationCompat.PRIORITY_LOW
            else                                    -> NotificationCompat.PRIORITY_DEFAULT
        }

        val tapIntent = PendingIntent.getActivity(
            this,
            ID_FCM_NOTIFICATION,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(ID_FCM_NOTIFICATION, notification)
        Timber.i("$TAG   ✓ Notificación publicada (id=$ID_FCM_NOTIFICATION, channel=$channel)")
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG       = "EternaFCM"
        const val TAG_TOKEN         = "EternaFCM_Token"   // filtro rápido en Logcat

        private const val DEFAULT_TITLE          = "EternaMente"
        private const val DATA_KEY_CHANNEL       = "channel"
        private const val DATA_KEY_TITLE         = "title"
        private const val DATA_KEY_BODY          = "body"
        private const val ID_FCM_NOTIFICATION    = 500

        /**
         * Obtiene el token FCM actual del dispositivo y lo registra en Logcat.
         *
         * Usar en debug o en pantallas de diagnóstico para obtener el token
         * y poder enviarlo desde Firebase Console / Postman durante pruebas.
         *
         * ```
         * // Ejemplo de uso desde cualquier punto del código:
         * EternaMenteMessagingService.fetchAndLogToken()
         * ```
         *
         * Para ver el token: Logcat → filtrar por tag "EternaFCM_Token"
         */
        fun fetchAndLogToken() {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    val preview = token.take(8) + "…"
                    Timber.i("$TAG ▶ Token FCM actual: $preview  (tag $TAG_TOKEN para completo)")
                    Timber.tag(TAG_TOKEN).i(token)   // completo — requiere filtro explícito
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "$TAG ✗ No se pudo obtener el token FCM")
                }
        }
    }
}
