package com.eternamente.app.presentation.games.engine

/**
 * Estados posibles de cualquier juego cognitivo en EternaMente.
 *
 * La UI observa el [kotlinx.coroutines.flow.StateFlow]<[GameState]> expuesto por
 * [GameEngine.state] para renderizar la pantalla correcta sin lógica de estado propia.
 *
 * Máquina de estados:
 * ```
 * Idle → Instructions → Countdown(3..1) → Playing → Paused → Playing → Completed
 *                                          ↑                               ↓
 *                                          └───── (resume) ────────────────┘
 * ```
 */
sealed class GameState {

    /** Estado inicial — el motor existe pero el juego no ha empezado. */
    object Idle : GameState()

    /**
     * Mostrando las instrucciones del juego al usuario.
     * El motor hace la transición a [Countdown] cuando el usuario confirma.
     */
    object Instructions : GameState()

    /**
     * Cuenta regresiva visible antes de que empiecen los estímulos.
     *
     * @property seconds Segundos restantes (típicamente 3, 2, 1).
     */
    data class Countdown(val seconds: Int) : GameState()

    /**
     * Juego activo — mostrando estímulos y recibiendo respuestas.
     *
     * @property progress  Fracción de avance del juego [0.0, 1.0].
     *                     Basado en ítems completados o tiempo transcurrido.
     * @property timeLeft  Segundos restantes si hay tiempo límite; `null` si es sin límite.
     */
    data class Playing(val progress: Float, val timeLeft: Int?) : GameState()

    /**
     * Juego pausado por el usuario o por pérdida de foco de la app.
     * Se retoma con [GameEngine.resume].
     */
    object Paused : GameState()

    /**
     * Juego terminado con un resultado calculado.
     *
     * @property result Resultado del motor (nivel de engine). Para el resultado de dominio
     *                  persistido en Room, ver [GameBaseViewModel.buildDomainResult].
     */
    data class Completed(val result: GameResult) : GameState()
}
