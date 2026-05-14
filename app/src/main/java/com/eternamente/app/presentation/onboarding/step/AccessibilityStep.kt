package com.eternamente.app.presentation.onboarding.step

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.onboarding.AccessibilityFormState
import com.eternamente.app.presentation.onboarding.FontScale
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay

/**
 * Paso 4 del onboarding — Configuración de accesibilidad.
 *
 * **Vista previa en tiempo real:**
 * El texto de muestra actualiza su tamaño inmediatamente al mover el slider.
 * Esto permite al usuario calibrar el tamaño antes de confirmar.
 *
 * **Haptic feedback:**
 * Cuando el switch de vibración está activo, cada cambio de control genera
 * vibración usando [LocalHapticFeedback] para demostrarlo in-situ.
 *
 * **Tema inmediato:**
 * Los switches de alto contraste y modo oscuro cambian [AccessibilityFormState]
 * que se persistirá en DataStore al completar. La aplicación del tema completo
 * ocurre al reiniciar la app con las preferencias guardadas.
 *
 * **Persistencia:**
 * Al pulsar "¡Listo!" el ViewModel llama a [UserPreferencesRepository.savePreferences]
 * y luego a [UserRepository.registerUser] + [UserRepository.recordConsent].
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AccessibilityStep(
    accessibilityForm: AccessibilityFormState,
    onFontScaleChanged: (FontScale) -> Unit,
    onHighContrastChanged: (Boolean) -> Unit,
    onHapticFeedbackChanged: (Boolean) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    isLoading: Boolean,
    error: String?,
    onClearError: () -> Unit,
    onComplete: () -> Unit
) {
    val haptic       = LocalHapticFeedback.current
    val scrollState  = rememberScrollState()

    // Notification permission — only needed on Android 13+ (API 33)
    val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    // Limpiar error automáticamente después de 4 segundos
    LaunchedEffect(error) {
        if (error != null) {
            delay(4_000)
            onClearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        // ── Título ────────────────────────────────────────────────────────────
        Text(
            text  = "Personaliza tu experiencia",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text  = "Puedes cambiar esto más adelante en Ajustes",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // ── Slider: Tamaño de letra ────────────────────────────────────────────
        Text(
            text  = "Tamaño de letra",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))

        // Labels de las posiciones
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FontScale.entries.forEach { scale ->
                Text(
                    text      = scale.label,
                    style     = MaterialTheme.typography.bodySmall,
                    color     = if (scale == accessibilityForm.fontScale)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.weight(1f)
                )
            }
        }

        Slider(
            value         = accessibilityForm.fontScale.ordinal.toFloat(),
            onValueChange = { raw ->
                val index  = raw.toInt().coerceIn(0, FontScale.entries.size - 1)
                val newScale = FontScale.entries[index]
                if (newScale != accessibilityForm.fontScale) {
                    onFontScaleChanged(newScale)
                    if (accessibilityForm.hapticFeedback) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            },
            valueRange    = 0f..(FontScale.entries.size - 1).toFloat(),
            steps         = FontScale.entries.size - 2,  // 2 stops intermedios entre 4 posiciones
            modifier      = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Tamaño de letra: ${accessibilityForm.fontScale.label}. " +
                        "Desliza para cambiar entre Pequeño, Normal, Grande y Muy grande"
                },
            colors        = SliderDefaults.colors(
                thumbColor       = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        // ── Vista previa del tamaño de fuente ────────────────────────────────
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier          = Modifier.padding(16.dp),
                contentAlignment  = Alignment.Center
            ) {
                Text(
                    text  = "Así se verá el texto en EternaMente",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (18f * accessibilityForm.fontScale.scale).sp
                    ),
                    color         = MaterialTheme.colorScheme.onSurface,
                    textAlign     = TextAlign.Center,
                    modifier      = Modifier.semantics {
                        contentDescription = "Vista previa del tamaño ${accessibilityForm.fontScale.label}: Así se verá el texto en EternaMente"
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Switch: Alto contraste ─────────────────────────────────────────────
        AccessibilitySwitch(
            label              = "Alto contraste",
            description        = "Mayor diferencia entre colores para mejor visibilidad",
            checked            = accessibilityForm.highContrast,
            contentDescription = "Activar alto contraste${if (accessibilityForm.highContrast) ", activo" else ""}",
            onCheckedChange    = { enabled ->
                onHighContrastChanged(enabled)
                if (accessibilityForm.hapticFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // ── Switch: Vibración ─────────────────────────────────────────────────
        AccessibilitySwitch(
            label              = "Vibración al responder",
            description        = "Retroalimentación háptica cuando pulsas en los juegos",
            checked            = accessibilityForm.hapticFeedback,
            contentDescription = "Activar vibración al responder${if (accessibilityForm.hapticFeedback) ", activo" else ""}",
            onCheckedChange    = { enabled ->
                onHapticFeedbackChanged(enabled)
                // Vibrar para demostrar el efecto al activar
                if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )

        Spacer(Modifier.height(16.dp))

        // ── Switch: Modo oscuro ────────────────────────────────────────────────
        AccessibilitySwitch(
            label              = "Modo oscuro",
            description        = "Fondo oscuro para reducir la fatiga visual en ambientes con poca luz",
            checked            = accessibilityForm.darkMode,
            contentDescription = "Activar modo oscuro${if (accessibilityForm.darkMode) ", activo" else ""}",
            onCheckedChange    = { enabled ->
                onDarkModeChanged(enabled)
                if (accessibilityForm.hapticFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        // ── Notificaciones ────────────────────────────────────────────────────
        NotificationPermissionCard(notifPermission)

        Spacer(Modifier.height(24.dp))

        // ── Error (si existe) ─────────────────────────────────────────────────
        if (error != null) {
            Text(
                text     = error,
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Error: $error" }
            )
            Spacer(Modifier.height(12.dp))
        }

        // ── Botón final ────────────────────────────────────────────────────────
        EternaFullWidthButton(
            text               = "¡Listo! Comenzar evaluación",
            onClick            = onComplete,
            isLoading          = isLoading,
            contentDescription = if (isLoading)
                "Guardando tu configuración, por favor espera"
            else
                "Finalizar configuración y comenzar la evaluación cognitiva"
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ── Notification permission card ──────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NotificationPermissionCard(
    permissionState: com.google.accompanist.permissions.PermissionState?
) {
    // On Android < 13 POST_NOTIFICATIONS is auto-granted — nothing to show
    if (permissionState == null) return

    val granted = permissionState.status.isGranted

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = if (granted) Icons.Filled.NotificationsActive
                                     else         Icons.Filled.Notifications,
                contentDescription = null,
                tint               = if (granted) MaterialTheme.colorScheme.primary
                                     else         MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(28.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Recordatorios diarios",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    if (granted) "Recibirás un recordatorio diario para tu sesión ✓"
                    else         "Activa las notificaciones para no olvidar tu sesión",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!granted) {
                Button(
                    onClick = { permissionState.launchPermissionRequest() },
                    colors  = ButtonDefaults.buttonColors()
                ) {
                    Text("Activar")
                }
            }
        }
    }
}

// ── Switch reutilizable ───────────────────────────────────────────────────────

@Composable
private fun AccessibilitySwitch(
    label: String,
    description: String,
    checked: Boolean,
    contentDescription: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text  = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            modifier        = Modifier.semantics {
                this.contentDescription = contentDescription
            }
        )
    }
}
