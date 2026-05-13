package com.eternamente.app.presentation.games.memorymatch

/**
 * Conjuntos de símbolos visuales para las tarjetas del Memorama.
 *
 * **Criterios de diseño para adultos mayores:**
 * - Solo símbolos visuales, SIN texto en las tarjetas.
 * - Progresión de familiaridad: animales (conocidos) → geométricas → abstractos.
 * - Colores integrados en los emojis para apoyo visual adicional.
 *
 * @property symbols Lista de símbolos únicos. El juego toma los primeros N para N pares.
 */
enum class CardSet(val symbols: List<String>) {

    /** Niveles 1–2: animales familiares, alta familiaridad cultural. */
    ANIMALS(listOf(
        "🐶", "🐱", "🐰", "🐻", "🐸", "🐠", "🦋", "🐙",
        "🦁", "🐨", "🦊", "🐯", "🐧", "🦚", "🦜", "🐬",
        "🦀", "🦋", "🐌", "🦔"
    )),

    /** Niveles 3–4: formas geométricas coloridas, abstractas pero reconocibles. */
    GEOMETRIC(listOf(
        "🔴", "🔵", "🟢", "🟡", "🟠", "🟣", "⚫", "⚪",
        "🔶", "🔷", "🔸", "🔹", "🔺", "🔻", "💠", "🔘",
        "🔳", "🔲", "▪️", "▫️"
    )),

    /** Nivel 5: símbolos abstractos variados, mayor dificultad de discriminación. */
    SYMBOLS(listOf(
        "⭐", "❤️", "🌙", "☁️", "⚡", "🌊", "🔥", "❄️",
        "🌸", "🍄", "🌵", "⛰️", "🌈", "💎", "🗝️", "🎯",
        "🎭", "🎪", "🎨", "🎬"
    ))
}
