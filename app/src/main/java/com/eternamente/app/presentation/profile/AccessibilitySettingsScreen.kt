package com.eternamente.app.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsScreen(
    innerPadding: PaddingValues,
    onNavigateBack: () -> Unit
) {
    val viewModel: AccessibilitySettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = Modifier.padding(innerPadding),
        topBar   = {
            TopAppBar(
                title = {
                    Text(
                        "Accesibilidad",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Visualización ──────────────────────────────────────────────────
            AccessibilitySection(title = "Visualización") {
                AccessibilityToggleRow(
                    icon            = Icons.Filled.Contrast,
                    label           = "Alto contraste",
                    description     = "Aumenta la diferencia de colores para mejor visibilidad (WCAG AAA)",
                    checked         = state.highContrast,
                    onCheckedChange = viewModel::toggleHighContrast
                )
                AccessibilityToggleRow(
                    icon            = Icons.Filled.DarkMode,
                    label           = "Modo oscuro",
                    description     = "Fondo oscuro — reduce fatiga visual en ambientes con poca luz",
                    checked         = state.darkMode,
                    onCheckedChange = viewModel::toggleDarkMode
                )
                AccessibilityToggleRow(
                    icon            = Icons.Filled.TextFields,
                    label           = "Texto grande",
                    description     = "Aumenta el tamaño de letra al 115% en toda la app",
                    checked         = state.largeText,
                    onCheckedChange = viewModel::toggleLargeText
                )
            }

            // ── Movimiento y animaciones ───────────────────────────────────────
            AccessibilitySection(title = "Movimiento") {
                AccessibilityToggleRow(
                    icon            = Icons.Filled.Waves,
                    label           = "Reducir animaciones",
                    description     = "Elimina las animaciones de volteo y transiciones — útil si las animaciones causan molestia",
                    checked         = state.reducedMotion,
                    onCheckedChange = viewModel::toggleReducedMotion
                )
                AccessibilityToggleRow(
                    icon            = Icons.Filled.Vibration,
                    label           = "Respuesta háptica",
                    description     = "Vibración al responder en los juegos cognitivos",
                    checked         = state.hapticFeedback,
                    onCheckedChange = viewModel::toggleHapticFeedback
                )
            }

            // ── Lector de pantalla ─────────────────────────────────────────────
            AccessibilitySection(title = "Lector de pantalla") {
                AccessibilityToggleRow(
                    icon            = Icons.Filled.RecordVoiceOver,
                    label           = "Modo TalkBack",
                    description     = "Activa descripciones de voz adicionales en toda la app",
                    checked         = state.talkBackMode,
                    onCheckedChange = viewModel::toggleTalkBackMode
                )
                TalkBackInfoCard()
            }

            // ── Vista previa en tiempo real ────────────────────────────────────
            AccessibilitySection(title = "Vista previa") {
                AccessibilityPreviewCard(state = state)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Sección ───────────────────────────────────────────────────────────────────

@Composable
private fun AccessibilitySection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text     = title,
            style    = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column { content() }
        }
    }
}

// ── Toggle row ────────────────────────────────────────────────────────────────

@Composable
private fun AccessibilityToggleRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            modifier        = Modifier.semantics {
                contentDescription = "$label: ${if (checked) "activado" else "desactivado"}"
            }
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color    = MaterialTheme.colorScheme.outlineVariant
    )
}

// ── Info card sobre TalkBack ──────────────────────────────────────────────────

@Composable
private fun TalkBackInfoCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text  = "Para activar TalkBack del sistema, ve a Ajustes del dispositivo → " +
                    "Accesibilidad → TalkBack. EternaMente es compatible con TalkBack en " +
                    "todos los juegos excepto Trazado de Sendero y Dibujo de Reloj.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Vista previa ──────────────────────────────────────────────────────────────

@Composable
private fun AccessibilityPreviewCard(state: AccessibilitySettingsState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Así se verá la app con tu configuración:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Tarjeta de muestra con los ajustes actuales
        val previewTextScale = if (state.largeText) 1.15f else 1f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(
                    width = if (state.highContrast) 2.dp else 0.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text  = "¡Buenos días, vanne!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (MaterialTheme.typography.titleMedium.fontSize.value * previewTextScale).let {
                            androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp)
                        },
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text  = "Sesión cognitiva de hoy",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * previewTextScale).let {
                            androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp)
                        }
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                // Indicadores de configuración activa
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.highContrast) StatusChip("Alto contraste")
                    if (state.largeText)    StatusChip("Texto grande")
                    if (state.darkMode)     StatusChip("Modo oscuro")
                    if (state.reducedMotion) StatusChip("Sin animaciones")
                }
            }
        }

        // Accesibilidad de la vista previa para TalkBack
        Box(
            modifier = Modifier.semantics {
                contentDescription = buildString {
                    append("Vista previa con ")
                    if (state.highContrast) append("alto contraste, ")
                    if (state.largeText) append("texto grande, ")
                    if (state.darkMode) append("modo oscuro, ")
                    if (state.reducedMotion) append("animaciones reducidas, ")
                    if (!state.highContrast && !state.largeText && !state.darkMode && !state.reducedMotion)
                        append("configuración estándar")
                }
            }
        ) {}
    }
}

@Composable
private fun StatusChip(label: String) {
    Text(
        text     = label,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
