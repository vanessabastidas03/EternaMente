package com.eternamente.app.presentation.games.memorymatch

/**
 * Estado inmutable de una tarjeta individual en el Memorama.
 *
 * Las tarjetas se actualizan creando nuevas instancias (patrón inmutable) para
 * que Compose detecte los cambios de estado de forma eficiente.
 *
 * @property id        Índice único en el grid [0, totalCards).
 * @property symbol    Símbolo visual que se muestra al voltear (emoji sin texto).
 * @property isFaceUp  `true` cuando la tarjeta está volteada y muestra el símbolo.
 * @property isMatched `true` cuando esta tarjeta ya formó par correctamente.
 */
data class CardState(
    val id: Int,
    val symbol: String,
    val isFaceUp: Boolean  = false,
    val isMatched: Boolean = false
) {
    /** Una tarjeta no interactuable está ya volteada o emparejada. */
    val isInteractable: Boolean get() = !isFaceUp && !isMatched
}
