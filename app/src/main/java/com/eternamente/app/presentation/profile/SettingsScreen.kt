package com.eternamente.app.presentation.profile

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.ui.theme.EternaMenteTheme

// ── Screen ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onNavigateToAccessibility: () -> Unit,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state     by viewModel.state.collectAsState()
    val loggingOut by viewModel.loggingOut.collectAsState()

    // Collect one-shot logout completion → trigger navigation
    LaunchedEffect(Unit) {
        viewModel.logoutComplete.collect { onLogout() }
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(innerPadding),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ajustes",
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
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Apariencia ────────────────────────────────────────────────────
            SettingsSection(title = "Apariencia") {
                SettingsToggleRow(
                    icon             = Icons.Filled.DarkMode,
                    label            = "Modo oscuro",
                    description      = "Interfaz con fondo oscuro",
                    checked          = state.darkMode,
                    onCheckedChange  = viewModel::toggleDarkMode
                )
                SettingsToggleRow(
                    icon             = Icons.Filled.Contrast,
                    label            = "Alto contraste",
                    description      = "Mejora legibilidad del texto",
                    checked          = state.highContrast,
                    onCheckedChange  = viewModel::toggleHighContrast
                )
            }

            // ── Accesibilidad ─────────────────────────────────────────────────
            SettingsSection(title = "Accesibilidad") {
                SettingsToggleRow(
                    icon             = Icons.Filled.Vibration,
                    label            = "Respuesta háptica",
                    description      = "Vibración al responder en los juegos",
                    checked          = state.hapticFeedback,
                    onCheckedChange  = viewModel::toggleHapticFeedback
                )
                SettingsNavRow(
                    icon        = Icons.Filled.Accessibility,
                    label       = "Ajustes de accesibilidad",
                    description = "Tamaño de texto y más opciones",
                    onClick     = onNavigateToAccessibility
                )
            }

            // ── Cuenta ────────────────────────────────────────────────────────
            SettingsSection(title = "Cuenta") {
                // Logout button — prominent but with confirmation guard
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)
                    ),
                    border   = CardDefaults.outlinedCardBorder()
                        .copy(width = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Logout,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.error,
                            modifier           = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Cerrar sesión",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.error
                                )
                            )
                            Text(
                                "Tu progreso se guardará en este dispositivo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick  = { showLogoutDialog = true },
                            enabled  = !loggingOut,
                            modifier = Modifier.semantics {
                                contentDescription = "Botón cerrar sesión"
                            }
                        ) {
                            if (loggingOut) {
                                CircularProgressIndicator(
                                    modifier  = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color     = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    "Salir",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // ── Confirmation dialog ───────────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    Icons.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("¿Cerrar sesión?") },
            text  = {
                Text(
                    "Tu progreso se guardará. Podrás volver a ingresar con tu correo y PIN.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cerrar sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ── Reusable settings components ──────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
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

@Composable
private fun SettingsToggleRow(
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
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            modifier        = Modifier.semantics { contentDescription = "$label: ${if (checked) "activado" else "desactivado"}" }
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
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
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Ir a $label", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    EternaMenteTheme {
        // Stateless preview — pass empty lambdas
    }
}
