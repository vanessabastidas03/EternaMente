package com.eternamente.app.presentation.games.engine

/**
 * Retroalimentación que el motor devuelve inmediatamente tras recibir un [UserInput].
 *
 * La UI usa este valor para lanzar la animación/sonido de feedback sin esperar
 * a que el motor recalcule el estado completo del juego.
 */
sealed class InputFeedback {

    /** La respuesta es correcta — mostrar animación de acierto (verde, sonido positivo). */
    object Correct : InputFeedback()

    /** La respuesta es incorrecta — mostrar animación de error (rojo, vibración). */
    object Incorrect : InputFeedback()

    /**
     * El input fue aceptado pero el juego aún no puede juzgarlo como correcto/incorrecto
     * (ej. el usuario está escribiendo una secuencia que se evalúa al completarse).
     */
    object Accepted : InputFeedback()

    /**
     * El input fue ignorado porque llegó en un momento inválido
     * (ej. durante la cuenta regresiva o cuando el juego está pausado).
     */
    object Ignored : InputFeedback()

    /**
     * Feedback con tipo de animación específica.
     *
     * @property type Tipo de animación a reproducir.
     */
    data class WithAnimation(val type: AnimationType) : InputFeedback()
}

/** Tipos de animación de feedback disponibles. */
enum class AnimationType { SUCCESS, ERROR, NEUTRAL, HIGHLIGHT }
