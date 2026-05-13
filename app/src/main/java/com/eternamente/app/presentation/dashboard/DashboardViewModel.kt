package com.eternamente.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.core.Result
import com.eternamente.app.core.getOrNull
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.SessionType
import com.eternamente.app.domain.repository.GamificationRepository
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * ViewModel del Dashboard. Carga datos en paralelo desde Room y los expone
 * como [StateFlow]<[DashboardState]> para consumo reactivo en el composable.
 *
 * ## Estrategia de carga
 * 1. Muestra el skeleton (`isLoading = true`).
 * 2. Lanza consultas a Room en paralelo con `async/await` en [Dispatchers.IO].
 * 3. Actualiza el estado en un solo `update` atómico para evitar recomposiciones parciales.
 * 4. En caso de error, mantiene `isLoading = false` y emite [DashboardEvent.ShowSnackbar].
 *
 * ## Sin bloqueo del hilo principal
 * Todas las operaciones de Room se ejecutan en [Dispatchers.IO].
 * La UI solo observa [StateFlow] / [Flow] — nunca llama a suspend directamente.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val gamificationRepository: GamificationRepository,
    private val mlRepository: MlRepository,
    private val gameResultRepository: GameResultRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _state  = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events: Flow<DashboardEvent> = _events.receiveAsFlow()

    init { loadDashboard() }

    /** Recarga todos los datos (útil para pull-to-refresh). */
    fun refresh() = loadDashboard()

    // ── Carga principal ───────────────────────────────────────────────────────

    private fun loadDashboard() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }

            val userId = userPreferencesRepository.getCurrentUserId()
            if (userId == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            runCatching {
                // ── Consultas paralelas a Room ────────────────────────────────
                val userDeferred        = async { userRepository.getUserById(userId) }
                val gamificationDeferred = async { gamificationRepository.getProfile(userId) }
                val predictionDeferred  = async { mlRepository.getLatestPrediction(userId) }

                val userResult        = userDeferred.await()
                val gamificationResult = gamificationDeferred.await()
                val predictionResult  = predictionDeferred.await()

                // ── Sesión de hoy ─────────────────────────────────────────────
                val zone     = ZoneId.systemDefault()
                val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                val todaySession   = getTodaySession(userId, dayStart)
                val weekProgress   = calculateWeekProgress(userId)
                val todayResults   = countTodayGameResults(userId, dayStart)
                val totalExpected  = expectedGamesFor(todaySession)

                // ── Alerta activa ─────────────────────────────────────────────
                val prediction = (predictionResult as? Result.Success)?.data
                val activeAlert = prediction?.takeIf {
                    it.alertLevel == AlertLevel.ALERT || it.alertLevel == AlertLevel.WATCH
                }

                // ── Estado final (un solo update atómico) ─────────────────────
                val userName  = (userResult as? Result.Success)?.data?.name
                    ?.split(" ")?.firstOrNull() ?: ""
                val profile   = (gamificationResult as? Result.Success)?.data
                val weekPct   = weekProgress.count { it } * 100 / 7

                _state.update { current ->
                    current.copy(
                        isLoading          = false,
                        userName           = userName,
                        todaySession       = todaySession,
                        todayCompleted     = todaySession?.completed == true,
                        todayGameResults   = todayResults,
                        totalGamesExpected = totalExpected,
                        pointsEarnedToday  = if (todaySession?.completed == true) POINTS_PER_SESSION else 0,
                        streak             = profile?.currentStreak ?: 0,
                        totalPoints        = profile?.totalPoints ?: 0,
                        weekProgress       = weekProgress,
                        weekCompletionPct  = weekPct,
                        activeAlert        = activeAlert
                    )
                }
            }.onFailure { exception ->
                _state.update { it.copy(isLoading = false, error = exception.message) }
                _events.send(
                    DashboardEvent.ShowSnackbar("No se pudieron cargar tus datos. Intenta de nuevo.")
                )
            }
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Devuelve la sesión más reciente de hoy si existe.
     * Recibe [dayStart] ya calculado en [loadDashboard] para evitar recalcularlo.
     */
    private suspend fun getTodaySession(userId: String, dayStart: Long): CognitiveSession? {
        val dayEnd = dayStart + DAY_MS - 1
        val latest = sessionRepository.getLatestSession(userId).getOrNull()
        return latest?.takeIf { it.sessionDate in dayStart..dayEnd }
    }

    /**
     * Calcula los últimos 7 días como lista de booleanos.
     * - Índice 0 → hace 6 días (izquierda en la barra semanal)
     * - Índice 6 → hoy (derecha en la barra semanal)
     */
    private suspend fun calculateWeekProgress(userId: String): List<Boolean> {
        val zone  = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return (6 downTo 0).map { daysAgo ->
            val date     = today.minusDays(daysAgo.toLong())
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd   = dayStart + DAY_MS - 1
            (sessionRepository.countCompletedSessions(userId, dayStart, dayEnd)
                .getOrNull() ?: 0) > 0
        }
    }

    /**
     * Cuenta todos los juegos completados hoy por el usuario, sumando resultados
     * de todas las sesiones que iniciaron hoy (no solo la más reciente).
     */
    private suspend fun countTodayGameResults(userId: String, dayStart: Long): Int =
        runCatching {
            gameResultRepository.countGameResultsForUserToday(userId, dayStart).getOrNull() ?: 0
        }.getOrDefault(0)

    /** Devuelve el número de juegos esperados según el tipo de sesión. */
    private fun expectedGamesFor(session: CognitiveSession?): Int = when (session?.type) {
        SessionType.WEEKLY_FULL, SessionType.BASELINE -> 6
        SessionType.DAILY, null                       -> 3
    }

    private companion object {
        const val POINTS_PER_SESSION = 15   // Puntos base por sesión completada
        const val DAY_MS             = 86_400_000L
    }
}
