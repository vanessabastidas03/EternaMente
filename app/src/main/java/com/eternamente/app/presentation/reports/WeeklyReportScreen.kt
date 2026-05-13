package com.eternamente.app.presentation.reports

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.ui.theme.EternaMenteTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    innerPadding: PaddingValues,
    onNavigateToMonthly: () -> Unit,
    onNavigateToAlertDetail: (alertId: String) -> Unit
) {
    val viewModel: WeeklyReportViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = Modifier.padding(innerPadding),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Reportes",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToMonthly) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Ver reporte mensual")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { scaffoldPadding ->

        // Error snackbar
        state.errorMessage?.let { msg ->
            LaunchedEffect(msg) {
                // shown via snackbar below; auto-dismiss after re-compose
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // ── Latest prediction card ──────────────────────────────────
                AnimatedContent(
                    targetState = state.prediction,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "prediction_card"
                ) { prediction ->
                    if (prediction != null) {
                        PredictionCard(
                            prediction   = prediction,
                            lastRunLabel = state.lastRunLabel
                        )
                    } else {
                        NoPredictionCard()
                    }
                }

                // ── Error message ───────────────────────────────────────────
                state.errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = viewModel::clearError) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                            }
                        }
                    }
                }

                // ── Analizar ahora button ───────────────────────────────────
                Button(
                    onClick   = viewModel::runManualAnalysis,
                    enabled   = !state.isAnalyzing,
                    modifier  = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    AnimatedContent(
                        targetState = state.isAnalyzing,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "analyze_btn"
                    ) { analyzing ->
                        if (analyzing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color       = MaterialTheme.colorScheme.onPrimary
                                )
                                Text("Analizando…", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text("Analizar ahora", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // ── Monthly report link ─────────────────────────────────────
                OutlinedButton(
                    onClick  = onNavigateToMonthly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ver reporte mensual")
                }
            }
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun PredictionCard(prediction: MlPrediction, lastRunLabel: String?) {
    val (levelColor, levelIcon, levelLabel) = when (prediction.alertLevel) {
        AlertLevel.NORMAL -> Triple(Color(0xFF2E7D32), Icons.Filled.CheckCircle, "Sin cambios relevantes")
        AlertLevel.WATCH  -> Triple(Color(0xFFF57C00), Icons.Filled.Info,        "Algunos cambios detectados")
        AlertLevel.ALERT  -> Triple(Color(0xFFC62828), Icons.Filled.Warning,     "Cambios que merecen atención")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Header row: level chip + date
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment       = Alignment.CenterVertically,
                horizontalArrangement   = Arrangement.SpaceBetween
            ) {
                SuggestionChip(
                    onClick = {},
                    label   = { Text(levelLabel, style = MaterialTheme.typography.labelMedium) },
                    icon    = { Icon(levelIcon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors  = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = levelColor.copy(alpha = 0.12f),
                        labelColor     = levelColor,
                        iconContentColor = levelColor
                    )
                )
                lastRunLabel?.let { date ->
                    Text(
                        date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Explanation
            Text(
                prediction.explanation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Risk score indicator
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Índice de variación",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "%.0f%%".format(prediction.riskScore * 100f),
                        style = MaterialTheme.typography.labelMedium,
                        color = levelColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress      = { prediction.riskScore },
                    modifier      = Modifier.fillMaxWidth(),
                    color         = levelColor,
                    trackColor    = levelColor.copy(alpha = 0.15f)
                )
            }

            // Flagged domains
            if (prediction.domainsFlagged.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "Áreas con cambios:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    prediction.domainsFlagged.forEach { domain ->
                        AssistChip(
                            onClick = {},
                            label   = { Text(domainLabel(domain), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoPredictionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment     = Alignment.CenterHorizontally,
            verticalArrangement     = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Analytics,
                contentDescription = null,
                modifier           = Modifier.size(48.dp),
                tint               = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
            Text(
                "Aún no hay análisis disponible",
                style  = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Completa al menos 7 sesiones de juegos para ver tu primer análisis cognitivo. " +
                "Puedes iniciarlo manualmente con el botón de abajo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun domainLabel(domain: CognitiveDomain): String = when (domain) {
    CognitiveDomain.MEMORY           -> "Memoria"
    CognitiveDomain.ATTENTION        -> "Atención"
    CognitiveDomain.EXECUTIVE        -> "Función ejecutiva"
    CognitiveDomain.LANGUAGE         -> "Lenguaje"
    CognitiveDomain.ORIENTATION      -> "Orientación"
    CognitiveDomain.PROCESSING_SPEED -> "Velocidad"
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeeklyReportPreview() {
    EternaMenteTheme { /* stateless preview */ }
}
