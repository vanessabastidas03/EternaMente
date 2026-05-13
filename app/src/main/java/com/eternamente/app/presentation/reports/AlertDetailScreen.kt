package com.eternamente.app.presentation.reports

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eternamente.app.presentation.common.PlaceholderScreen

/**
 * Detalle ampliado de una alerta cognitiva generada por el modelo ML.
 *
 * Muestra:
 * - [MlPrediction.alertLevel] con indicador visual de severidad.
 * - [MlPrediction.riskScore] con barra de progreso.
 * - [MlPrediction.domainsFlagged] con explicación por dominio.
 * - [MlPrediction.explanation] en lenguaje accesible para adultos mayores.
 * - Recomendación clínica acorde al [AlertLevel].
 *
 * @param innerPadding   Padding del [Scaffold] padre.
 * @param alertId        UUID de la [MlPrediction] a mostrar.
 * @param onNavigateBack Volver al reporte que originó la alerta.
 */
@Composable
fun AlertDetailScreen(
    innerPadding: PaddingValues,
    alertId: String,
    onNavigateBack: () -> Unit
) {
    PlaceholderScreen(
        screenName         = "Detalle de alerta\n$alertId",
        accessibilityLabel = "Detalle de la alerta cognitiva $alertId con recomendaciones",
        innerPadding       = innerPadding,
        primaryActionLabel = "Volver",
        onPrimaryAction    = onNavigateBack
    )
}
