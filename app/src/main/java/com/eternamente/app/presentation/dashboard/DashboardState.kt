package com.eternamente.app.presentation.dashboard

import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.navigation.Screen

// ══════════════════════════════════════════════════════════════════════════════
// Estado de la pantalla Dashboard
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Estado completo del Dashboard. Es la única fuente de verdad que observan los
 * composables — no hay lógica de presentación fuera de [DashboardViewModel].
 *
 * @property isLoading          `true` mientras los datos se cargan desde Room.
 * @property userName           Primer nombre del usuario (ej. "Ana").
 * @property todaySession       Sesión del día actual; `null` si no ha iniciado ninguna.
 * @property todayCompleted     `true` si la sesión de hoy está marcada como completada.
 * @property todayGameResults   Número de juegos completados en la sesión de hoy.
 * @property totalGamesExpected Juegos esperados según el tipo de sesión (3 DAILY, 6 WEEKLY).
 * @property pointsEarnedToday  Puntos obtenidos en la sesión de hoy.
 * @property streak             Racha actual en días consecutivos.
 * @property totalPoints        Puntos acumulados del usuario.
 * @property weekProgress       Lista de 7 booleanos: índice 0 = hace 6 días, índice 6 = hoy.
 * @property weekCompletionPct  Porcentaje de días completados esta semana (0–100).
 * @property activeAlert        Predicción ML activa con nivel WATCH o ALERT; `null` si no hay.
 * @property error              Mensaje de error o `null` si no hay error.
 */
data class DashboardState(
    val isLoading: Boolean               = true,
    val userName: String                 = "",
    val todaySession: CognitiveSession?  = null,
    val todayCompleted: Boolean          = false,
    val todayGameResults: Int            = 0,
    val totalGamesExpected: Int          = 3,
    val pointsEarnedToday: Int           = 0,
    val streak: Int                      = 0,
    val totalPoints: Int                 = 0,
    val weekProgress: List<Boolean>      = List(7) { false },
    val weekCompletionPct: Int           = 0,
    val activeAlert: MlPrediction?       = null,
    val error: String?                   = null
) {
    /** Fracción de progreso de la sesión actual (0.0–1.0). */
    val sessionProgress: Float
        get() = if (totalGamesExpected > 0) {
            (todayGameResults.toFloat() / totalGamesExpected).coerceIn(0f, 1f)
        } else 0f

    /** `true` si hay una sesión en progreso (iniciada pero no completada). */
    val sessionInProgress: Boolean
        get() = todaySession != null && !todayCompleted
}

// ══════════════════════════════════════════════════════════════════════════════
// Eventos de un solo disparo del Dashboard
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Eventos que el ViewModel emite UNA sola vez hacia la UI
 * (no se repiten en recomposiciones).
 *
 * [Navigate] encapsula el destino de navegación para que el ViewModel
 * no necesite conocer el [NavHostController].
 * [ShowSnackbar] muestra un mensaje efímero al usuario.
 */
sealed class DashboardEvent {
    data class Navigate(val screen: Screen) : DashboardEvent()
    data class ShowSnackbar(val message: String) : DashboardEvent()
}
