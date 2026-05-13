package com.eternamente.app.presentation.games.memorymatch

import com.eternamente.app.presentation.games.engine.GameEngine
import com.eternamente.app.presentation.games.engine.GameState
import com.eternamente.app.presentation.games.engine.GameTimer
import com.eternamente.app.presentation.games.engine.InputFeedback
import com.eternamente.app.presentation.games.engine.MetricsCollector
import com.eternamente.app.presentation.games.engine.SystemTimeProvider
import com.eternamente.app.presentation.games.engine.UserInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Motor del juego Memorama de Pares.
 *
 * ## Mecánica
 * - Grid de [MemoryMatchConfig.totalCards] tarjetas boca abajo.
 * - El usuario voltea 2 tarjetas por turno:
 *   - **Par correcto** → se quedan visibles, +[POINTS_PER_MATCH] puntos.
 *   - **Par incorrecto** → delay de [FLIP_BACK_DELAY_MS] ms y se voltean de nuevo, -[PENALTY_PER_MISS] puntos.
 * - El juego termina cuando todos los pares son encontrados o se agota el tiempo.
 *
 * ## Thread safety
 * - El scope interno usa `Dispatchers.Default + limitedParallelism(1)` para
 *   garantizar que todas las mutaciones de estado sean secuenciales.
 * - [MutableStateFlow] proporciona actualizaciones atómicas y thread-safe.
 * - [AtomicBoolean] / [AtomicReference] para variables accedidas desde el hilo UI.
 */
class MemoryMatchEngine(
    override val config: MemoryMatchConfig
) : GameEngine<MemoryMatchConfig, MemoryMatchResult> {

    // ── Estado del motor ──────────────────────────────────────────────────────

    private val _state  = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()

    override val metrics = MetricsCollector(SystemTimeProvider())

    // ── Estado del grid ───────────────────────────────────────────────────────

    private val _cards   = MutableStateFlow<List<CardState>>(emptyList())
    val cards: StateFlow<List<CardState>> = _cards.asStateFlow()

    private val _score   = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    // ── Variables de control ──────────────────────────────────────────────────

    /** Índice de la primera tarjeta del turno actual (`null` = esperando primera carta). */
    private val firstFlippedIndex = AtomicReference<Int?>(null)

    /** `true` durante el delay de [FLIP_BACK_DELAY_MS] (previene input adicional). */
    private val isCheckingPair = AtomicBoolean(false)

    private val matchedPairs     = AtomicInteger(0)
    private val turnsCount       = AtomicInteger(0)
    private var timeRemaining    = config.timeLimitSeconds
    private var finalResult: MemoryMatchResult? = null

    // ── Scope y timer ─────────────────────────────────────────────────────────

    private val scope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(1) + SupervisorJob()
    )

    private val timer = GameTimer(
        durationSeconds = config.timeLimitSeconds,
        timeProvider    = SystemTimeProvider(),
        scope           = scope,
        onTick          = { secondsLeft ->
            timeRemaining = secondsLeft
            if (_state.value is GameState.Playing) {
                _state.value = GameState.Playing(
                    progress = matchedPairs.get().toFloat() / config.totalPairs,
                    timeLeft = secondsLeft
                )
            }
        },
        onComplete = { scope.launch { endGame(timedOut = true) } }
    )

    // ── Inicialización del grid ───────────────────────────────────────────────

    init {
        val symbols   = config.cardSet.symbols.take(config.totalPairs)
        val shuffled  = (symbols + symbols).shuffled()
        _cards.value  = shuffled.mapIndexed { idx, sym -> CardState(id = idx, symbol = sym) }
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun start() {
        _state.value = GameState.Instructions
    }

    /** Inicia la cuenta regresiva 3-2-1 y luego arranca el juego. */
    fun startCountdown() {
        scope.launch {
            for (i in 3 downTo 1) {
                _state.value = GameState.Countdown(i)
                delay(COUNTDOWN_TICK_MS)
            }
            _state.value = GameState.Playing(progress = 0f, timeLeft = config.timeLimitSeconds)
            timer.start()
        }
    }

    override fun pause() {
        if (_state.value !is GameState.Playing) return
        timer.pause()
        _state.value = GameState.Paused
    }

    override fun resume() {
        if (_state.value !is GameState.Paused) return
        _state.value = GameState.Playing(
            progress = matchedPairs.get().toFloat() / config.totalPairs,
            timeLeft = timeRemaining
        )
        timer.resume()
    }

    // ── Input del usuario ─────────────────────────────────────────────────────

    override fun onInput(input: UserInput): InputFeedback {
        if (input !is UserInput.TapTarget)                    return InputFeedback.Ignored
        if (_state.value !is GameState.Playing)               return InputFeedback.Ignored
        if (isCheckingPair.get())                             return InputFeedback.Ignored

        val index = input.targetId.toIntOrNull()              ?: return InputFeedback.Ignored
        val currentCards = _cards.value
        val card = currentCards.getOrNull(index)              ?: return InputFeedback.Ignored
        if (!card.isInteractable)                             return InputFeedback.Ignored

        // Voltear la tarjeta inmediatamente (feedback visual instantáneo)
        _cards.value = currentCards.toMutableList().also { it[index] = card.copy(isFaceUp = true) }

        val previous = firstFlippedIndex.getAndSet(null)

        return if (previous == null) {
            // Primera tarjeta del turno: registrar estímulo
            firstFlippedIndex.set(index)
            metrics.recordStimulusShown()
            InputFeedback.Accepted
        } else {
            // Segunda tarjeta: comprobar si forman par
            val isMatch = currentCards[previous].symbol == card.symbol
            metrics.recordUserResponse(isCorrect = isMatch)
            turnsCount.incrementAndGet()

            if (isMatch) {
                scope.launch { handleMatch(previous, index) }
                InputFeedback.Correct
            } else {
                scope.launch { handleMismatch(previous, index) }
                InputFeedback.Incorrect
            }
        }
    }

    // ── Lógica de par correcto / incorrecto ───────────────────────────────────

    private suspend fun handleMatch(firstIdx: Int, secondIdx: Int) {
        _score.value = _score.value + POINTS_PER_MATCH

        // Breve delay para que el usuario vea ambas tarjetas antes de marcarlas
        delay(MATCH_REVEAL_MS)

        _cards.value = _cards.value.toMutableList().also { list ->
            list[firstIdx]  = list[firstIdx].copy(isMatched = true)
            list[secondIdx] = list[secondIdx].copy(isMatched = true)
        }

        val total = matchedPairs.incrementAndGet()
        if (total == config.totalPairs) {
            endGame(timedOut = false)
        } else {
            _state.value = GameState.Playing(
                progress = total.toFloat() / config.totalPairs,
                timeLeft = timeRemaining
            )
        }
    }

    private suspend fun handleMismatch(firstIdx: Int, secondIdx: Int) {
        _score.value = (_score.value - PENALTY_PER_MISS).coerceAtLeast(0)
        isCheckingPair.set(true)

        // El usuario ve ambas tarjetas durante FLIP_BACK_DELAY_MS
        delay(FLIP_BACK_DELAY_MS)

        _cards.value = _cards.value.toMutableList().also { list ->
            list[firstIdx]  = list[firstIdx].copy(isFaceUp = false)
            list[secondIdx] = list[secondIdx].copy(isFaceUp = false)
        }
        isCheckingPair.set(false)
    }

    // ── Fin del juego ─────────────────────────────────────────────────────────

    private suspend fun endGame(timedOut: Boolean) {
        timer.cancel()
        val snapshot = metrics.getMetrics()

        finalResult = MemoryMatchResult(
            gameId               = config.gameId,
            sessionId            = config.sessionId,
            metrics              = snapshot,
            difficultyReached    = config.difficultyLevel,
            totalPairs           = config.totalPairs,
            matchedPairs         = matchedPairs.get(),
            score                = _score.value,
            timeRemainingSeconds = if (timedOut) 0 else timeRemaining,
            turnsCount           = turnsCount.get(),
            completedByTimeout   = timedOut
        )

        _state.value = GameState.Completed(finalResult!!)
    }

    override fun forceEnd(): MemoryMatchResult {
        return finalResult ?: runBlocking {
            endGame(timedOut = false)
            finalResult!!
        }
    }

    // ── Constantes ────────────────────────────────────────────────────────────

    companion object {
        const val POINTS_PER_MATCH     = 10
        const val PENALTY_PER_MISS     = 2
        const val FLIP_BACK_DELAY_MS   = 1_000L   // 1 s — usuario ve ambas tarjetas
        const val MATCH_REVEAL_MS      = 400L     // Breve pausa antes de marcar par
        const val COUNTDOWN_TICK_MS    = 1_000L
    }
}
