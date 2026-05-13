package com.eternamente.app.presentation.games.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.usecase.SaveGameResultUseCase
import com.eternamente.app.domain.usecase.UpdateGamificationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.eternamente.app.domain.model.GameResult as DomainGameResult

/**
 * ViewModel base abstracto para todos los juegos cognitivos de EternaMente.
 *
 * ## Responsabilidades
 * 1. **Delegar al motor**: [startGame], [pauseGame], [resumeGame], [onUserInput]
 *    envuelven las llamadas al [GameEngine] correspondiente.
 * 2. **Observar la finalización**: cuando [GameEngine.state] emite [GameState.Completed],
 *    ejecuta automáticamente el flujo de guardado y navegación.
 * 3. **Guardar resultado**: convierte el resultado del motor a [DomainGameResult] via
 *    [buildDomainResult] y lo persiste en Room usando [SaveGameResultUseCase].
 * 4. **Actualizar gamificación**: llama a [UpdateGamificationUseCase] con la sesión
 *    y el resultado, para actualizar puntos y racha del usuario.
 * 5. **Navegar**: emite [GameNavigationEvent.NavigateToResult] para que la UI navegue
 *    a la pantalla de resultado sin conocer el NavController directamente.
 *
 * ## Hilt y herencia
 * `GameBaseViewModel` no es `@HiltViewModel` porque Hilt no puede inyectar en clases
 * abstractas. Los subclases concretas usan `@HiltViewModel` e inyectan las dependencias
 * comunes para pasarlas al constructor de `GameBaseViewModel`:
 * ```kotlin
 * @HiltViewModel
 * class StroopViewModel @Inject constructor(
 *     saveGameResultUseCase: SaveGameResultUseCase,
 *     updateGamificationUseCase: UpdateGamificationUseCase,
 *     sessionRepository: SessionRepository,
 *     userPreferencesRepository: UserPreferencesRepository
 * ) : GameBaseViewModel<StroopConfig, StroopResult>(
 *     saveGameResultUseCase, updateGamificationUseCase,
 *     sessionRepository, userPreferencesRepository
 * ) {
 *     override val engine: GameEngine<StroopConfig, StroopResult> = StroopEngine(...)
 *     override fun buildDomainResult(engineResult: StroopResult): DomainGameResult = ...
 * }
 * ```
 *
 * @param C  Tipo de configuración del juego.
 * @param R  Tipo de resultado del motor (nivel engine).
 * @param saveGameResultUseCase     Persiste el resultado en Room.
 * @param updateGamificationUseCase Actualiza puntos y racha tras la sesión.
 * @param sessionRepository         Carga la sesión para pasarla al UseCase de gamificación.
 * @param userPreferencesRepository Obtiene el userId activo del DataStore.
 */
abstract class GameBaseViewModel<C : GameConfig, R : GameResult>(
    private val saveGameResultUseCase: SaveGameResultUseCase,
    private val updateGamificationUseCase: UpdateGamificationUseCase,
    private val sessionRepository: SessionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // ── Motor — cada subclase lo provee ──────────────────────────────────────

    /** Motor de juego específico para este tipo de juego cognitivo. */
    protected abstract val engine: GameEngine<C, R>

    // ── Estado observable ─────────────────────────────────────────────────────

    /**
     * Refleja [GameEngine.state] para que la UI observe el estado del juego
     * a través del ViewModel sin acceder al engine directamente.
     */
    val gameState: StateFlow<GameState>
        get() = engine.state

    // ── Eventos de un solo disparo (navegación) ───────────────────────────────

    private val _navigationEvent = MutableSharedFlow<GameNavigationEvent>(extraBufferCapacity = 1)

    /**
     * Flujo de eventos de navegación emitidos cuando el juego termina.
     * La UI recolecta este Flow con `LaunchedEffect(Unit)` y navega según el evento.
     */
    val navigationEvent: SharedFlow<GameNavigationEvent> = _navigationEvent.asSharedFlow()

    // ── Control del juego ─────────────────────────────────────────────────────

    /**
     * Inicia el motor y comienza a observar la finalización del juego.
     * Debe llamarse desde la UI cuando el composable está listo (ej. en `LaunchedEffect`).
     */
    fun startGame() {
        engine.start()
        observeGameCompletion()
    }

    /** Pausa el juego activo. No-op si no está en [GameState.Playing]. */
    fun pauseGame() = engine.pause()

    /** Retoma el juego pausado. No-op si no está en [GameState.Paused]. */
    fun resumeGame() = engine.resume()

    /**
     * Envía un input del usuario al motor y retorna el feedback inmediato.
     *
     * @param input Acción del usuario (toque, selección, etc.).
     * @return [InputFeedback] para animar la UI inmediatamente.
     */
    fun onUserInput(input: UserInput): InputFeedback = engine.onInput(input)

    // ── Observación y guardado al completar ───────────────────────────────────

    private fun observeGameCompletion() {
        viewModelScope.launch {
            engine.state
                .filterIsInstance<GameState.Completed>()
                .collect { _ ->
                    // forceEnd() es idempotente si el juego ya completó naturalmente.
                    val engineResult = engine.forceEnd()
                    handleGameCompleted(engineResult)
                    return@collect   // Solo procesar el primer Completed
                }
        }
    }

    private suspend fun handleGameCompleted(engineResult: R) {
        withContext(Dispatchers.IO) {
            val domainResult = buildDomainResult(engineResult)

            // 1 — Guardar resultado en Room
            saveGameResultUseCase(domainResult)

            // 2 — Actualizar gamificación (puntos, racha, medallas)
            val session = loadSession(engineResult.sessionId)
            if (session != null) {
                updateGamificationUseCase(session, listOf(domainResult))
            }

            // 3 — Emitir evento de navegación a la pantalla de resultado
            _navigationEvent.emit(
                GameNavigationEvent.NavigateToResult(
                    gameId = domainResult.gameId,
                    score  = domainResult.scoreNormalized
                )
            )
        }
    }

    private suspend fun loadSession(sessionId: String): CognitiveSession? =
        sessionRepository.getSessionById(sessionId).getOrNull()

    // ── Conversión de resultado — implementada por cada juego ─────────────────

    /**
     * Convierte el resultado del motor ([R]) al modelo de dominio ([DomainGameResult])
     * que se persiste en Room y se muestra en la pantalla de resultado.
     *
     * Implementar en cada subclase aplicando la normalización de puntuación correcta
     * para el dominio cognitivo que evalúa ese juego.
     *
     * @param engineResult Resultado raw del motor con métricas ya calculadas.
     * @return [DomainGameResult] listo para persistir en Room.
     */
    protected abstract fun buildDomainResult(engineResult: R): DomainGameResult

    // ── Limpieza ──────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        engine.metrics.reset()   // Libera memoria de la lista de ensayos al destruir el VM
    }

    // ── Helper para Result<T>.getOrNull ──────────────────────────────────────

    private fun <T> com.eternamente.app.core.Result<T>.getOrNull(): T? =
        (this as? com.eternamente.app.core.Result.Success)?.data
}

// ══════════════════════════════════════════════════════════════════════════════
// Eventos de navegación del motor de juego
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Eventos de navegación emitidos por [GameBaseViewModel.navigationEvent].
 */
sealed class GameNavigationEvent {

    /**
     * El juego terminó — navegar a la pantalla de resultado.
     *
     * @param gameId Identificador del juego (para cargar el resultado correcto).
     * @param score  Puntuación normalizada [0–100] del resultado.
     */
    data class NavigateToResult(val gameId: String, val score: Float) : GameNavigationEvent()
}
