package com.eternamente.app.presentation.reports

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Reporte cognitivo mensual con comparativa de baseline.
 *
 * Contenido previsto:
 * - Gráfico de línea (MPAndroidChart) mostrando la tendencia de [overallScore]
 *   durante los últimos 30 días.
 * - Diferencia porcentual respecto al [CognitiveBaseline].
 * - [MlPrediction] más reciente con su [AlertLevel].
 * - Lista de alertas del mes → navegar a [AlertDetailScreen].
 *
 * @param innerPadding             Padding del [Scaffold] padre.
 * @param onNavigateToAlertDetail  Abrir el detalle de una alerta cognitiva.
 * @param onNavigateBack           Volver al reporte semanal.
 */
@Composable
fun MonthlyReportScreen(
    innerPadding: PaddingValues,
    onNavigateToAlertDetail: (alertId: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    PlaceholderScreen(
        screenName           = "Reporte mensual",
        accessibilityLabel   = "Reporte de evolución cognitiva del último mes con comparativa de baseline",
        innerPadding         = innerPadding,
        primaryActionLabel   = "Ver alerta de ejemplo",
        onPrimaryAction      = { onNavigateToAlertDetail("alert-demo-002") },
        secondaryActionLabel = "Volver",
        onSecondaryAction    = onNavigateBack
    )
}
