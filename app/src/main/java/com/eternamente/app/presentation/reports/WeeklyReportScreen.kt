package com.eternamente.app.presentation.reports

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Reporte cognitivo semanal.
 *
 * Contenido previsto:
 * - Gráfico de barras (MPAndroidChart) con la evolución por dominio cognitivo.
 * - Comparativa con la semana anterior.
 * - Lista de sesiones completadas en la semana.
 * - Alertas activas → navegar a [AlertDetailScreen].
 *
 * @param innerPadding             Padding del [Scaffold] padre.
 * @param onNavigateToMonthly      Ver el reporte mensual.
 * @param onNavigateToAlertDetail  Abrir el detalle de una alerta cognitiva.
 */
@Composable
fun WeeklyReportScreen(
    innerPadding: PaddingValues,
    onNavigateToMonthly: () -> Unit,
    onNavigateToAlertDetail: (alertId: String) -> Unit
) {
    PlaceholderScreen(
        screenName           = "Reporte semanal",
        accessibilityLabel   = "Reporte de tu evolución cognitiva de la última semana",
        innerPadding         = innerPadding,
        primaryActionLabel   = "Ver reporte mensual",
        onPrimaryAction      = onNavigateToMonthly,
        secondaryActionLabel = "Ver alerta de ejemplo",
        onSecondaryAction    = { onNavigateToAlertDetail("alert-demo-001") }
    )
}
