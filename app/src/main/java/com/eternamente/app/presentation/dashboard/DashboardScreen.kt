package com.eternamente.app.presentation.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.presentation.dashboard.components.CognitiveAlertCard
import com.eternamente.app.presentation.dashboard.components.DashboardHeader
import com.eternamente.app.presentation.dashboard.components.DashboardSkeleton
import com.eternamente.app.presentation.dashboard.components.QuickAccessRow
import com.eternamente.app.presentation.dashboard.components.StreakWidget
import com.eternamente.app.presentation.dashboard.components.TodaySessionCard
import com.eternamente.app.presentation.dashboard.components.WeeklySummaryWidget
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Pantalla principal de EternaMente — vista diaria del usuario.
 *
 * ## Arquitectura de la pantalla
 * - [DashboardViewModel] carga los datos en paralelo en [Dispatchers.IO].
 * - Mientras carga, se muestra [DashboardSkeleton] con efecto shimmer.
 * - Los datos se renderizan en un [LazyColumn] con pull-to-refresh.
 * - Los eventos one-shot ([DashboardEvent.ShowSnackbar]) se recolectan con [LaunchedEffect].
 *
 * ## Estructura visual (top → bottom)
 * 1. **[DashboardHeader]** — saludo personalizado + fecha
 * 2. **[TodaySessionCard]** — estado de la sesión del día
 * 3. **[StreakWidget]** — racha con Lottie si streak ≥ 3
 * 4. **[WeeklySummaryWidget]** — 7 círculos de la semana
 * 5. **[QuickAccessRow]** — 3 tarjetas de acceso rápido
 * 6. **[CognitiveAlertCard]** — solo si hay alerta activa
 *
 * @param innerPadding               Padding del [Scaffold] del [NavGraph] (incluye altura del BottomBar).
 * @param onNavigateToGameCatalog    Navegar al catálogo de juegos.
 * @param onNavigateToReport         Navegar al reporte semanal.
 * @param onNavigateToAchievements   Navegar a la galería de logros.
 * @param onNavigateToSettings       Navegar a ajustes.
 * @param onNavigateToAlertDetail    Navegar al detalle de una alerta (recibe el alertId).
 */

@Composable
fun DashboardScreen(
    innerPadding: PaddingValues,
    onNavigateToGameCatalog: () -> Unit,
    onNavigateToReport: () -> Unit       = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToSettings: () -> Unit     = {},
    onNavigateToAlertDetail: (alertId: String) -> Unit = {}
) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Recolectar eventos one-shot sin perder ninguno entre recomposiciones
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DashboardEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message)
                is DashboardEvent.Navigate ->
                    Unit  // La navegación real se gestiona vía callbacks del NavGraph
            }
        }
    }

    Scaffold(
        modifier     = Modifier.padding(innerPadding),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->

        // ── Skeleton de carga ─────────────────────────────────────────────────
        if (state.isLoading) {
            DashboardSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
            )
            return@Scaffold
        }

        // ── Contenido con pull-to-refresh ─────────────────────────────────────
        Box(


            modifier     = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            LazyColumn(
                contentPadding    = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                modifier          = Modifier.fillMaxSize()
            ) {

                // 1. Header personalizado
                item(key = "header") {
                    DashboardHeader(userName = state.userName)
                    Spacer(Modifier.height(8.dp))
                }

                // 2. Tarjeta de sesión de hoy
                item(key = "today_session") {
                    TodaySessionCard(
                        todayCompleted    = state.todayCompleted,
                        sessionInProgress = state.sessionInProgress,
                        gameResults       = state.todayGameResults,
                        totalGames        = state.totalGamesExpected,
                        sessionProgress   = state.sessionProgress,
                        pointsEarned      = state.pointsEarnedToday,
                        onStartSession    = onNavigateToGameCatalog,
                        modifier          = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 3. Widget de racha
                item(key = "streak") {
                    StreakWidget(
                        streak   = state.streak,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 4. Resumen semanal
                item(key = "weekly_summary") {
                    WeeklySummaryWidget(
                        weekProgress  = state.weekProgress,
                        completionPct = state.weekCompletionPct,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 5. Acceso rápido
                item(key = "quick_access") {
                    QuickAccessRow(
                        onNavigateToReport       = onNavigateToReport,
                        onNavigateToAchievements = onNavigateToAchievements,
                        onNavigateToSettings     = onNavigateToSettings,
                        modifier                 = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 6. Alerta cognitiva (solo si hay una activa)
                state.activeAlert?.let { alert ->
                    item(key = "alert") {
                        CognitiveAlertCard(
                            alert         = alert,
                            onViewDetails = { onNavigateToAlertDetail(alert.id) },
                            modifier      = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Espacio final para que el BottomBar no tape el último elemento
                item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Dashboard skeleton — Claro", showBackground = true)
@Preview(name = "Dashboard skeleton — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardSkeletonPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DashboardSkeleton()
        }
    }
}

@Preview(name = "Dashboard cargado — Claro", showBackground = true)
@Composable
private fun DashboardLoadedPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Vista previa estática sin ViewModel — composables individuales
            androidx.compose.foundation.lazy.LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                item { DashboardHeader(userName = "Ana") }
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    TodaySessionCard(
                        todayCompleted    = false, sessionInProgress = false,
                        gameResults       = 0,     totalGames = 3,
                        sessionProgress   = 0f,    pointsEarned = 0,
                        onStartSession    = {}
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item { StreakWidget(streak = 5) }
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    WeeklySummaryWidget(
                        weekProgress  = listOf(true, true, false, true, true, false, false),
                        completionPct = 57
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    QuickAccessRow(
                        onNavigateToReport = {}, onNavigateToAchievements = {}, onNavigateToSettings = {}
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    CognitiveAlertCard(
                        alert = MlPrediction(
                            id = "1", userId = "u1", predictionDate = 0L, riskScore = 0.4f,
                            alertLevel = AlertLevel.WATCH,
                            domainsFlagged = listOf(com.eternamente.app.domain.model.CognitiveDomain.MEMORY),
                            explanation = "Tendencia descendente detectada en memoria. Mantén tus sesiones."
                        ),
                        onViewDetails = {}
                    )
                }
            }
        }
    }
}
