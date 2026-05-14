package com.eternamente.app.presentation.reports

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportScreen(
    innerPadding:  PaddingValues,
    onNavigateBack: () -> Unit,
    reportState:   ReportState
) {
    val viewModel: PdfExportViewModel = hiltViewModel()
    val uiState   by viewModel.uiState.collectAsState()
    val context   = LocalContext.current

    // Collect one-shot share intent and launch it
    LaunchedEffect(Unit) {
        viewModel.shareIntent.collect { intent ->
            context.startActivity(intent)
        }
    }

    Scaffold(
        modifier = Modifier.padding(innerPadding),
        topBar = {
            TopAppBar(
                title = {
                    Text("Exportar PDF",
                         style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetToIdle()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { scaffoldPadding ->
        AnimatedContent(
            targetState  = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label        = "pdf_state"
        ) { state ->
            when (state) {
                is PdfExportUiState.Idle       -> IdleContent(
                    fileName    = state.expectedFileName,
                    scaffoldPadding = scaffoldPadding,
                    onSave      = { viewModel.generateAndSave(reportState) },
                    onShare     = { viewModel.generateAndShare(reportState) },
                    onCancel    = onNavigateBack
                )
                is PdfExportUiState.Generating -> GeneratingContent(scaffoldPadding)
                is PdfExportUiState.Success    -> SuccessContent(
                    file           = state.file,
                    scaffoldPadding = scaffoldPadding,
                    onShare        = { viewModel.generateAndShare(reportState) },
                    onDone         = onNavigateBack
                )
                is PdfExportUiState.Error      -> ErrorContent(
                    message         = state.message,
                    scaffoldPadding = scaffoldPadding,
                    onRetry         = { viewModel.resetToIdle() },
                    onCancel        = onNavigateBack
                )
            }
        }
    }
}

// ── Idle state ────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(
    fileName: String,
    scaffoldPadding: PaddingValues,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .padding(24.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.PictureAsPdf,
            contentDescription = null,
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Reporte listo para exportar",
            style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Se generará el archivo:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        // Filename preview chip
        Surface(
            shape  = RoundedCornerShape(8.dp),
            color  = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.InsertDriveFile, null, modifier = Modifier.size(18.dp),
                     tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    fileName,
                    style  = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color  = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Guardado en almacenamiento interno del dispositivo",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        // Action buttons
        Button(
            onClick  = onShare,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Compartir reporte")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick  = onSave,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Guardar en el dispositivo")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar")
        }
    }
}

// ── Generating state ──────────────────────────────────────────────────────────

@Composable
private fun GeneratingContent(scaffoldPadding: PaddingValues) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(56.dp))
            Text(
                "Generando reporte PDF…",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Esto tomará unos segundos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Success state ─────────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(
    file: File,
    scaffoldPadding: PaddingValues,
    onShare: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .padding(24.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(80.dp)
                .background(Color(0xFFE8F5E9), RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint     = Color(0xFF2E7D32)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "¡PDF generado con éxito!",
            style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            file.name,
            style  = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color  = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Guardado en almacenamiento interno",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Compartir (WhatsApp, email…)")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Listo")
        }
    }
}

// ── Error state ───────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    message: String,
    scaffoldPadding: PaddingValues,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .padding(24.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint     = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No se pudo generar el PDF",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}
