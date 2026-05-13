package com.eternamente.app.presentation.games.memorymatch

import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult as DomainGameResult
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.usecase.SaveGameResultUseCase
import com.eternamente.app.domain.usecase.UpdateGamificationUseCase
import com.eternamente.app.presentation.games.engine.GameBaseViewModel
import com.eternamente.app.presentation.games.engine.GameEngine
import com.eternamente.app.presentation.games.engine.UserInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel del juego Memorama de Pares.
 *
 * Extiende [GameBaseViewModel] para heredar:
 * - Observación automática de [GameState.Completed].
 * - Guardado del resultado en Room via [SaveGameResultUseCase].
 * - Actualización de gamificación via [UpdateGamificationUseCase].
 * - Emisión de [com.eternamente.app.presentation.games.engine.GameNavigationEvent].
 *
 * ## Ciclo de uso
 * 1. La pantalla llama [initialize] con la configuración del nivel.
 * 2. Llama [startGame] para comenzar (transiciona a `Instructions`).
 * 3. Cuando el usuario confirma instrucciones, llama [startCountdown].
 * 4. El engine emite `Playing`, `Completed` automáticamente.
 * 5. Cuando recibe `Completed`, el base ViewModel guarda el resultado y navega.
 */
@HiltViewModel
class MemoryMatchViewModel @Inject constructor(
    saveGameResultUseCase: SaveGameResultUseCase,
    updateGamificationUseCase: UpdateGamificationUseCase,
    sessionRepository: SessionRepository,
    userPreferencesRepository: UserPreferencesRepository
) : GameBaseViewModel<MemoryMatchConfig, MemoryMatchResult>(
    saveGameResultUseCase, updateGamificationUseCase,
    sessionRepository, userPreferencesRepository
) {
    // ── Engine (lazy init) ────────────────────────────────────────────────────

    private var _engine: MemoryMatchEngine? = null

    override val engine: GameEngine<MemoryMatchConfig, MemoryMatchResult>
        get() = requireNotNull(_engine) { "Engine not initialized — call initialize() first." }

    // ── Estado adicional expuesto a la UI ─────────────────────────────────────

    private val _cards   = MutableStateFlow<List<CardState>>(emptyList())
    val cards: StateFlow<List<CardState>> = _cards.asStateFlow()

    private val _score   = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _config  = MutableStateFlow<MemoryMatchConfig?>(null)
    val config: StateFlow<MemoryMatchConfig?> = _config.asStateFlow()

    // ── Inicialización ────────────────────────────────────────────────────────

    /**
     * Crea el engine con la configuración del nivel y comienza a espejear sus flows.
     *
     * Idempotente: si ya fue inicializado, no vuelve a crear el engine.
     */
    fun initialize(memoryConfig: MemoryMatchConfig) {
        if (_engine != null) return
        val newEngine = MemoryMatchEngine(memoryConfig)
        _engine = newEngine
        _config.value = memoryConfig

        // Espejear cards y score del engine → flujos propios del VM
        viewModelScope.launch { newEngine.cards.collect { _cards.value = it } }
        viewModelScope.launch { newEngine.score.collect { _score.value = it } }
    }

    // ── Control del juego ─────────────────────────────────────────────────────

    /** Inicia la cuenta regresiva 3-2-1 → Playing. */
    fun startCountdown() = (_engine)?.startCountdown()

    /** Envía el toque sobre la tarjeta con [cardIndex] y retorna el feedback de input. */
    fun onCardTapped(cardIndex: Int): com.eternamente.app.presentation.games.engine.InputFeedback =
        onUserInput(UserInput.TapTarget(cardIndex.toString()))

    // ── Construcción del resultado de dominio ─────────────────────────────────

    override fun buildDomainResult(engineResult: MemoryMatchResult): DomainGameResult {
        val scoreNorm = normalizeScore(engineResult)
        return DomainGameResult(
            id                = UUID.randomUUID().toString(),
            sessionId         = engineResult.sessionId,
            gameId            = engineResult.gameId,
            domain            = CognitiveDomain.MEMORY,
            scoreRaw          = engineResult.score.toFloat(),
            scoreNormalized   = scoreNorm,
            reactionTimeMsAvg = engineResult.metrics.mean,
            reactionTimeMsP50 = engineResult.metrics.median,
            accuracyPct       = engineResult.metrics.accuracyPct,
            errorsCount       = engineResult.metrics.errorCount,
            difficultyLevel   = engineResult.difficultyReached
        )
    }

    /**
     * Normaliza la puntuación a [0, 100] considerando:
     * - 60 % por pares encontrados (memoria episódica pura).
     * - 25 % por eficiencia de turnos (menos turnos = mejor memoria de trabajo).
     * - 15 % por tiempo restante.
     */
    private fun normalizeScore(result: MemoryMatchResult): Float {
        if (result.totalPairs == 0) return 0f

        val matchScore   = result.completionRatio * 60f

        val minTurns     = result.totalPairs.toFloat()
        val actualTurns  = result.turnsCount.toFloat().coerceAtLeast(minTurns)
        val turnScore    = (minTurns / actualTurns) * 25f

        val maxTime      = _config.value?.timeLimitSeconds?.toFloat() ?: 90f
        val timeScore    = (result.timeRemainingSeconds.toFloat() / maxTime) * 15f

        return (matchScore + turnScore + timeScore).coerceIn(0f, 100f)
    }
}
