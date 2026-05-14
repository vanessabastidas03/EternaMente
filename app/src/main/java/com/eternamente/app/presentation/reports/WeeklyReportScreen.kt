package com.eternamente.app.presentation.reports

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.domain.model.CognitiveDomain
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    innerPadding: PaddingValues,
    onNavigateToMonthly: () -> Unit,
    onNavigateToAlertDetail: (alertId: String) -> Unit,
    onNavigateToPdfExport: () -> Unit
) {
    val viewModel: ReportViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es"))

    val weekTitle = if (!state.isLoading) {
        "Semana del ${state.weekStart.format(dateFormatter)} al ${state.weekEnd.format(dateFormatter)}"
    } else {
        "Reporte semanal"
    }

    Scaffold(
        modifier = Modifier.padding(innerPadding),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        weekTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isLoading) {
                WeeklySkeletonLoading()
            } else {
                BarChartCard(state = state, viewModel = viewModel)
                ComparisonCard(state = state)
                AdherenceCard(state = state)
                AiMessageCard(state = state)
                ButtonsRow(
                    onNavigateToMonthly = onNavigateToMonthly,
                    onNavigateToPdfExport = onNavigateToPdfExport
                )
                AnalyzeButton(
                    isAnalyzing = state.isAnalyzing,
                    onAnalyze = viewModel::runManualAnalysis
                )
            }
        }
    }
}

@Composable
private fun WeeklySkeletonLoading() {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(surfaceVariant, RoundedCornerShape(8.dp))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(surfaceVariant, RoundedCornerShape(8.dp))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(surfaceVariant, RoundedCornerShape(8.dp))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(surfaceVariant, RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun BarChartCard(state: ReportState, viewModel: ReportViewModel) {
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Puntuación por dominio",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(12.dp))
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                factory = { ctx ->
                    BarChart(ctx).apply {
                        description.isEnabled = false
                        legend.isEnabled = true
                        setDrawGridBackground(false)
                        setBackgroundColor(surfaceColor)
                        setTouchEnabled(false)

                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(false)
                            granularity = 1f
                            textSize = 14f
                            textColor = onSurfaceColor
                            valueFormatter = IndexAxisValueFormatter(
                                arrayOf("Mem", "Ate", "Eje", "Len", "Ori")
                            )
                        }
                        axisLeft.apply {
                            axisMinimum = 0f
                            axisMaximum = 100f
                            textSize = 12f
                            textColor = onSurfaceColor
                        }
                        axisRight.isEnabled = false
                    }
                },
                update = { chart ->
                    val entries = viewModel.generateWeeklyChartData()
                    val colors = listOf(
                        AndroidColor.parseColor("#1976D2"),
                        AndroidColor.parseColor("#388E3C"),
                        AndroidColor.parseColor("#F57C00"),
                        AndroidColor.parseColor("#7B1FA2"),
                        AndroidColor.parseColor("#0097A7")
                    )
                    val dataSet = BarDataSet(entries, "").apply {
                        setColors(colors)
                        valueTextSize = 14f
                        valueTextColor = onSurfaceColor
                    }
                    chart.data = BarData(dataSet)

                    chart.axisLeft.removeAllLimitLines()
                    if (state.baselineScore > 0f) {
                        val limitLine = LimitLine(state.baselineScore, "Tu base").apply {
                            lineColor = AndroidColor.GRAY
                            lineWidth = 1.5f
                            enableDashedLine(10f, 10f, 0f)
                            textColor = AndroidColor.GRAY
                            textSize = 11f
                        }
                        chart.axisLeft.addLimitLine(limitLine)
                    }

                    chart.invalidate()
                }
            )
        }
    }
}

@Composable
private fun ComparisonCard(state: ReportState) {
    val domains = listOf(
        CognitiveDomain.MEMORY,
        CognitiveDomain.ATTENTION,
        CognitiveDomain.EXECUTIVE,
        CognitiveDomain.LANGUAGE,
        CognitiveDomain.ORIENTATION
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Comparación con semana anterior",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(8.dp))
            domains.forEach { domain ->
                val current = state.weeklyDomainScores[domain] ?: 0f
                val previous = state.previousWeekDomainScores[domain] ?: 0f
                val delta = current - previous
                val isUp = delta >= 0f
                val arrowColor = if (isUp) Color(0xFF1565C0) else Color(0xFFE65100)
                val arrowIcon = if (isUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown
                val deltaLabel = if (isUp) "+%.0f pts".format(delta) else "%.0f pts".format(delta)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        domainDisplayName(domain),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "%.0f pts".format(current),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = arrowIcon,
                        contentDescription = null,
                        tint = arrowColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        deltaLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = arrowColor
                    )
                }
            }
        }
    }
}

@Composable
private fun AdherenceCard(state: ReportState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Adherencia semanal",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.daysCompletedThisWeek} de 7 días completados",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (state.daysCompletedThisWeek / 7f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AiMessageCard(state: ReportState) {
    val message = state.aiMessage.ifBlank {
        state.latestPrediction?.explanation ?: return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Resumen inteligente",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ButtonsRow(
    onNavigateToMonthly: () -> Unit,
    onNavigateToPdfExport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onNavigateToMonthly,
            modifier = Modifier.weight(1f)
        ) {
            Text("Ver historial")
        }
        OutlinedButton(
            onClick = onNavigateToPdfExport,
            modifier = Modifier.weight(1f)
        ) {
            Text("Exportar PDF")
        }
    }
}

@Composable
private fun AnalyzeButton(isAnalyzing: Boolean, onAnalyze: () -> Unit) {
    Button(
        onClick = onAnalyze,
        enabled = !isAnalyzing,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isAnalyzing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text("Analizando…", style = MaterialTheme.typography.labelLarge)
        } else {
            Text("Analizar ahora", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun domainDisplayName(domain: CognitiveDomain): String = when (domain) {
    CognitiveDomain.MEMORY -> "Memoria"
    CognitiveDomain.ATTENTION -> "Atención"
    CognitiveDomain.EXECUTIVE -> "Función ejecutiva"
    CognitiveDomain.LANGUAGE -> "Lenguaje"
    CognitiveDomain.ORIENTATION -> "Orientación"
    CognitiveDomain.PROCESSING_SPEED -> "Velocidad"
}
