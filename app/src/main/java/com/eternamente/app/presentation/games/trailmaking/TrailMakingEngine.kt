package com.eternamente.app.presentation.games.trailmaking

import androidx.compose.ui.geometry.Offset
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
import kotlin.math.sqrt
import kotlin.random.Random

class TrailMakingEngine(override val config: TrailMakingConfig) :
    GameEngine<TrailMakingConfig, TrailMakingResult> {

    private val _state   = MutableStateFlow<GameState>(GameState.Idle)
    override val state: StateFlow<GameState> = _state.asStateFlow()
    override val metrics = MetricsCollector(SystemTimeProvider())

    private val _uiState = MutableStateFlow(TrailMakingUiState())
    val uiState: StateFlow<TrailMakingUiState> = _uiState.asStateFlow()

    private val scope    = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private val timeProvider = SystemTimeProvider()

    private var startMillis     = 0L
    private var timeRemaining   = config.timeLimitSeconds
    private var sequenceErrors  = 0
    private var currentTarget   = 0
    private var connectedPath   = mutableListOf<Int>()
    private var finalResult: TrailMakingResult? = null

    private val sequence: List<String> = buildSequence()
    val nodes: List<TrailNode>         = generateNodes()

    private val timer = GameTimer(config.timeLimitSeconds, SystemTimeProvider(), scope,
        onTick = { left ->
            timeRemaining = left
            if (_state.value is GameState.Playing) {
                _state.value = GameState.Playing(
                    progress = currentTarget.toFloat() / config.nodeCount,
                    timeLeft = left
                )
                _uiState.value = _uiState.value.copy(timeLeft = left)
            }
        },
        onComplete = { scope.launch { endGame(timedOut = true) } }
    )

    private fun buildSequence(): List<String> {
        val labels = mutableListOf<String>()
        if (!config.isAlternating) {
            (1..config.nodeCount).forEach { labels.add("$it") }
        } else {
            var n = 1; var letter = 'A'
            while (labels.size < config.nodeCount) {
                labels.add("$n"); n++
                if (labels.size < config.nodeCount) { labels.add("$letter"); letter++ }
            }
        }
        return labels
    }

    internal fun generateNodes(): List<TrailNode> {
        val positions = mutableListOf<Offset>()
        val minDist   = 0.18f
        while (positions.size < config.nodeCount) {
            val candidate = Offset(
                Random.nextFloat() * 0.75f + 0.1f,
                Random.nextFloat() * 0.75f + 0.1f
            )
            if (positions.all { dist(it, candidate) >= minDist }) positions.add(candidate)
        }
        return positions.mapIndexed { i, pos -> TrailNode(label = sequence[i], position = pos, isTarget = i == 0) }
    }

    private fun dist(a: Offset, b: Offset): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    override fun start() { _state.value = GameState.Instructions }

    fun startCountdown() {
        scope.launch {
            for (i in 3 downTo 1) { _state.value = GameState.Countdown(i); delay(1000L) }
            startMillis = timeProvider.uptimeMillis()
            _uiState.value = _uiState.value.copy(nodes = nodes, currentTarget = 0, timeLeft = config.timeLimitSeconds)
            _state.value = GameState.Playing(progress = 0f, timeLeft = config.timeLimitSeconds)
            metrics.recordStimulusShown()
            timer.start()
        }
    }

    /** Llamado desde la pantalla cuando el dedo toca cerca de un nodo. */
    override fun onInput(input: UserInput): InputFeedback {
        if (_state.value !is GameState.Playing) return InputFeedback.Ignored
        if (input !is UserInput.TapTarget)       return InputFeedback.Ignored
        val tappedIndex = input.targetId.toIntOrNull() ?: return InputFeedback.Ignored

        return if (tappedIndex == currentTarget) {
            metrics.recordUserResponse(isCorrect = true)
            connectedPath.add(tappedIndex)
            currentTarget++
            val newNodes = nodes.mapIndexed { i, n ->
                n.copy(isConnected = i in connectedPath, isTarget = i == currentTarget)
            }
            _uiState.value = _uiState.value.copy(
                nodes = newNodes, connectedPath = connectedPath.toList(), currentTarget = currentTarget
            )
            if (currentTarget >= config.nodeCount) {
                scope.launch { endGame(timedOut = false) }
            } else {
                _state.value = GameState.Playing(currentTarget.toFloat() / config.nodeCount, timeRemaining)
            }
            InputFeedback.Correct
        } else {
            sequenceErrors++
            metrics.recordUserResponse(isCorrect = false)
            _uiState.value = _uiState.value.copy(sequenceErrors = sequenceErrors)
            InputFeedback.Incorrect
        }
    }

    fun updateTouchPath(path: List<Offset>) {
        _uiState.value = _uiState.value.copy(touchPath = path)
    }

    /** Check proximity from screen — called by composable on drag. Only active while Playing. */
    fun checkProximityAndConnect(normalizedPos: Offset): Boolean {
        if (_state.value !is GameState.Playing) return false
        if (currentTarget >= config.nodeCount) return false
        val target = nodes[currentTarget]
        return if (dist(normalizedPos, target.position) < PROXIMITY_RADIUS) {
            onInput(UserInput.TapTarget("$currentTarget"))
            true
        } else false
    }

    private suspend fun endGame(timedOut: Boolean) {
        timer.cancel()
        val elapsed = ((timeProvider.uptimeMillis() - startMillis) / 1000).toInt()
        val snap    = metrics.getMetrics()
        finalResult = TrailMakingResult(
            gameId=config.gameId, sessionId=config.sessionId, metrics=snap,
            difficultyReached=config.difficultyLevel,
            completedNodes=currentTarget, totalNodes=config.nodeCount,
            sequenceErrors=sequenceErrors,
            timeElapsedSeconds=elapsed,
            completedSuccessfully = currentTarget >= config.nodeCount
        )
        _state.value = GameState.Completed(finalResult!!)
    }

    override fun pause()  { timer.pause();  _state.value = GameState.Paused }
    override fun resume() { timer.resume(); _state.value = GameState.Playing(currentTarget.toFloat()/config.nodeCount, timeRemaining) }
    override fun forceEnd(): TrailMakingResult = finalResult ?: runBlocking { endGame(false); finalResult!! }

    companion object { const val PROXIMITY_RADIUS = 0.08f }
}
