package com.eternamente.app.presentation.games.engine

import kotlinx.coroutines.flow.StateFlow

/**
 * Motor genérico que implementan todos los juegos cognitivos de EternaMente.
 *
 * ## Contrato
 * El motor gestiona el ciclo de vida completo de un juego:
 * 1. **Inicio**: [start] → `Instructions` → `Countdown` → `Playing`.
 * 2. **Interacción**: [onInput] recibe cada respuesta del usuario y devuelve
 *    [InputFeedback] inmediato; [metrics] registra los RTs internamente.
 * 3. **Pausa/reanudación**: [pause] / [resume] — el estado de [GameState] cambia
 *    a [GameState.Paused] y de vuelta a [GameState.Playing].
 * 4. **Finalización natural**: el motor emite [GameState.Completed] con el resultado.
 * 5. **Finalización forzada**: [forceEnd] detiene el juego en cualquier momento
 *    y retorna el resultado parcial con las métricas acumuladas hasta ese punto.
 *
 * ## StateFlow
 * [state] es el único punto de verdad que observa la UI. Todos los cambios de
 * estado del juego (incluyendo el countdown y el progreso) se emiten aquí.
 *
 * ## Implementación
 * Cada juego crea su clase concreta que implementa esta interfaz:
 * ```kotlin
 * class StroopEngine(override val config: StroopConfig) : GameEngine<StroopConfig, StroopResult> {
 *     override val state: StateFlow<GameState> = _state.asStateFlow()
 *     override val metrics = MetricsCollector()
 *     override fun start() { ... }
 *     // etc.
 * }
 * ```
 *
 * @param Config  Tipo de configuración del juego. Debe implementar [GameConfig].
 * @param Result  Tipo de resultado del juego. Debe implementar [GameResult] (nivel engine).
 */
interface GameEngine<Config : GameConfig, Result : GameResult> {

    /** Configuración inmutable del juego. */
    val config: Config

    /**
     * Estado observable del juego. La UI observa este Flow para renderizar.
     * Emite siempre en el hilo principal (garantizado por `stateIn(Dispatchers.Main)`
     * o por `MutableStateFlow` con actualizaciones desde coroutines).
     */
    val state: StateFlow<GameState>

    /** Recopilador de métricas. Compartido entre el motor y [GameBaseViewModel]. */
    val metrics: MetricsCollector

    /**
     * Inicia el juego desde el estado [GameState.Idle].
     *
     * Emite la secuencia: `Instructions` → `Countdown(3)` → `Countdown(2)` →
     * `Countdown(1)` → `Playing(progress=0f, timeLeft=...)`.
     */
    fun start()

    /**
     * Pausa el juego activo.
     * Si el estado actual no es [GameState.Playing], no hace nada.
     */
    fun pause()

    /**
     * Retoma el juego pausado.
     * Si el estado actual no es [GameState.Paused], no hace nada.
     */
    fun resume()

    /**
     * Procesa un [UserInput] del usuario y devuelve retroalimentación inmediata.
     *
     * La retroalimentación se debe mostrar en la UI antes de esperar la siguiente
     * emisión de [state] para evitar lag perceptible.
     *
     * @param input Acción del usuario.
     * @return [InputFeedback] inmediato (correcto / incorrecto / ignorado).
     */
    fun onInput(input: UserInput): InputFeedback

    /**
     * Finaliza el juego forzosamente y devuelve el resultado parcial.
     *
     * Puede llamarse en cualquier estado. Si el juego ya emitió [GameState.Completed]
     * de forma natural, esta función es idempotente y devuelve el mismo resultado.
     *
     * **Uso típico:** el usuario cierra la app o el juego expira por timeout de sesión.
     * [GameBaseViewModel] también llama a [forceEnd] justo después de observar
     * `GameState.Completed` para obtener el resultado tipado `R`.
     *
     * @return El resultado acumulado hasta el momento de la llamada.
     */
    fun forceEnd(): Result
}
