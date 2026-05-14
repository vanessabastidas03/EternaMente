package com.eternamente.app.presentation.reports

import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.domain.model.AlertLevel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    innerPadding: PaddingValues,
    onNavigateToAlertDetail: (alertId: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val viewModel: ReportViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = Modifier.padding(innerPadding),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Reporte mensual",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { scaffoldPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isLoading) {
                repeat(4) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    )
                }
            } else {
                LineChartCard(state = state, viewModel = viewModel)
                CalendarHeatmapCard(state = state)
                CognitiveStatusCard(state = state)
                state.aiMessage.takeIf { it.isNotBlank() }?.let { AiRecommendationCard(it) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── LineChart card ─────────────────────────────────────────────────────────────

@Composable
private fun LineChartCard(state: ReportState, viewModel: ReportViewModel) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Evolución global (8 semanas)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            val entries  = remember(state.weeklyTrend) { viewModel.generateMonthlyChartData() }
            val baseline = state.baselineScore

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin datos suficientes para mostrar la evolución",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    factory  = { ctx ->
                        LineChart(ctx).apply {
                            description.isEnabled = false
                            setDrawGridBackground(false)
                            setTouchEnabled(false)
                            xAxis.apply {
                                position       = XAxis.XAxisPosition.BOTTOM
                                granularity    = 1f
                                setDrawGridLines(false)
                                textSize       = 14f
                                textColor      = onSurfaceColor
                                valueFormatter = IndexAxisValueFormatter(
                                    arrayOf("S1","S2","S3","S4","S5","S6","S7","S8")
                                )
                            }
                            axisLeft.apply {
                                axisMinimum = 0f
                                axisMaximum = 100f
                                textSize    = 12f
                                textColor   = onSurfaceColor
                            }
                            axisRight.isEnabled = false
                            legend.isEnabled    = false
                        }
                    },
                    update = { chart ->
                        val dataSet = LineDataSet(entries, "Score").apply {
                            color          = AColor.parseColor("#1976D2")
                            lineWidth      = 2f
                            circleRadius   = 4f
                            setCircleColor(AColor.parseColor("#1976D2"))
                            valueTextSize  = 11f
                            valueTextColor = onSurfaceColor
                            mode           = LineDataSet.Mode.CUBIC_BEZIER
                            setDrawFilled(true)
                            fillColor      = AColor.parseColor("#1976D2")
                            fillAlpha      = 30
                        }
                        chart.axisLeft.removeAllLimitLines()
                        if (baseline > 0f) {
                            chart.axisLeft.addLimitLine(
                                LimitLine(baseline, "Base").apply {
                                    lineWidth  = 1.5f
                                    enableDashedLine(10f, 5f, 0f)
                                    lineColor  = AColor.GRAY
                                    textColor  = AColor.GRAY
                                    textSize   = 10f
                                }
                            )
                        }
                        chart.data = LineData(dataSet)
                        chart.invalidate()
                    }
                )
            }
        }
    }
}

// ── Calendar heatmap ───────────────────────────────────────────────────────────

@Composable
private fun CalendarHeatmapCard(state: ReportState) {
    val now       = remember { YearMonth.now() }
    val monthName = remember(now) {
        now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es")))
            .replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Adherencia de $monthName",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(4.dp))
            CalendarGrid(
                yearMonth     = now,
                completedDays = state.completedDaysThisMonth
            )
            // Legend
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(Color(0xFF2E7D32), "Sesión completada")
                LegendDot(Color(0xFFE0E0E0), "Sin sesión")
            }
        }
    }
}

@Composable
private fun CalendarGrid(yearMonth: YearMonth, completedDays: Set<Int>) {
    val daysInMonth = yearMonth.lengthOfMonth()
    // ISO DayOfWeek: MON=1, ..., SUN=7. Convert to Sun-first offset (SUN=0)
    val offset    = yearMonth.atDay(1).dayOfWeek.value % 7
    val headers   = listOf("D","L","M","X","J","V","S")
    val totalRows = ((offset + daysInMonth) + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Day-of-week headers
        Row(Modifier.fillMaxWidth()) {
            headers.forEach { h ->
                Text(
                    h,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Day cells
        repeat(totalRows) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { dow ->
                    val dayNum = week * 7 + dow - offset + 1
                    Box(
                        modifier          = Modifier.weight(1f).aspectRatio(1f),
                        contentAlignment  = Alignment.Center
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val done = dayNum in completedDays
                            Box(
                                modifier         = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (done) Color(0xFF2E7D32) else Color(0xFFE0E0E0)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$dayNum",
                                    fontSize = 10.sp,
                                    color    = if (done) Color.White else Color(0xFF757575)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Cognitive status (semáforo) ───────────────────────────────────────────────

@Composable
private fun CognitiveStatusCard(state: ReportState) {
    val prediction = state.latestPrediction

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Estado cognitivo actual",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            if (prediction == null) {
                Text(
                    "Sin análisis disponible aún",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val (dotColor, statusLabel) = when (prediction.alertLevel) {
                    AlertLevel.NORMAL -> Color(0xFF2E7D32) to "Estado estable"
                    AlertLevel.WATCH  -> Color(0xFFF57C00) to "Variación detectada"
                    AlertLevel.ALERT  -> Color(0xFFC62828) to "Requiere atención"
                }
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(dotColor))
                    Text(
                        statusLabel,
                        style      = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color      = dotColor
                    )
                }
                Text(
                    prediction.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── AI recommendation card ────────────────────────────────────────────────────

@Composable
private fun AiRecommendationCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.Top
        ) {
            Icon(
                Icons.Filled.AutoAwesome, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Recomendación",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
