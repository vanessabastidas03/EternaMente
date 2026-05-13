package com.eternamente.app.presentation.games.engine

/**
 * Tipos de input que el usuario puede realizar durante un juego cognitivo.
 *
 * [GameEngine.onInput] recibe un [UserInput] y devuelve un [InputFeedback].
 * La jerarquía es extensible: cada juego puede añadir tipos específicos en sus
 * propios ficheros sin modificar este sealed class.
 */
sealed class UserInput {

    /** Toque simple sin objetivo específico (ej. botón de respuesta). */
    object Tap : UserInput()

    /**
     * Toque sobre un elemento identificable de la pantalla.
     *
     * @property targetId Identificador del elemento tocado (ej. `"card_3"`, `"option_A"`).
     */
    data class TapTarget(val targetId: String) : UserInput()

    /**
     * Selección de una opción de múltiple elección.
     *
     * @property optionIndex Índice base-0 de la opción seleccionada.
     */
    data class SelectOption(val optionIndex: Int) : UserInput()

    /**
     * Entrada numérica (ej. recordar y escribir una secuencia de dígitos).
     *
     * @property value Número ingresado por el usuario.
     */
    data class NumberInput(val value: Int) : UserInput()

    /**
     * Gesto de deslizamiento direccional.
     *
     * @property direction Dirección del gesto.
     */
    data class Swipe(val direction: SwipeDirection) : UserInput()

    /**
     * Señal de que el usuario no respondió a tiempo (omisión detectada por el motor).
     * Puede enviarse tanto por la UI como por el timer interno del engine.
     */
    object Omission : UserInput()
}

/** Dirección de un gesto de deslizamiento. */
enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }
