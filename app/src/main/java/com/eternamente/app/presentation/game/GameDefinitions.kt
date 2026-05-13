package com.eternamente.app.presentation.game

import com.eternamente.app.domain.model.CognitiveDomain

/** Descriptor estático de un juego cognitivo para el catálogo. */
data class GameDefinition(
    val gameId: String,
    val name: String,
    val domain: CognitiveDomain,
    val emoji: String,
    val shortDescription: String,
    val domainLabel: String
)

/** Los 15 juegos de EternaMente con sus metadatos de catálogo. */
val ALL_GAME_DEFINITIONS = listOf(
    GameDefinition("memory_match",         "Memorama de Pares",       CognitiveDomain.MEMORY,           "🃏", "Encuentra los pares",               "Memoria"),
    GameDefinition("digit_span",           "Secuencia de Números",    CognitiveDomain.MEMORY,           "🔢", "Repite la secuencia",                "Memoria"),
    GameDefinition("flash_color",          "Flash de Colores",        CognitiveDomain.ATTENTION,        "🎨", "Responde al color objetivo",         "Atención"),
    GameDefinition("trail_making",         "Conecta los Puntos",      CognitiveDomain.EXECUTIVE,        "🔗", "Conecta en orden",                   "Ejecutivo"),
    GameDefinition("naming_image",         "Nombra la Imagen",        CognitiveDomain.LANGUAGE,         "🖼️", "¿Cómo se llama esto?",              "Lenguaje"),
    GameDefinition("verbal_fluency",       "Palabras en Categoría",   CognitiveDomain.LANGUAGE,         "💬", "Escribe palabras del grupo",         "Lenguaje"),
    GameDefinition("spot_diff",            "Encuentra Diferencias",   CognitiveDomain.ATTENTION,        "🔍", "Toca las diferencias",               "Atención"),
    GameDefinition("stroop",               "Stroop de Colores",       CognitiveDomain.EXECUTIVE,        "🎭", "Responde al color de la tinta",      "Ejecutivo"),
    GameDefinition("corsi_block",          "Reproduce el Patrón",     CognitiveDomain.MEMORY,           "⬜", "Copia la secuencia de bloques",      "Memoria"),
    GameDefinition("temporal_orientation", "Orientación Temporal",    CognitiveDomain.ORIENTATION,      "📅", "¿Qué día es hoy?",                   "Orientación"),
    GameDefinition("clock_drawing",        "Dibuja el Reloj",         CognitiveDomain.EXECUTIVE,        "🕐", "Coloca las manecillas",              "Ejecutivo"),
    GameDefinition("face_name",            "Caras y Nombres",         CognitiveDomain.MEMORY,           "👥", "Recuerda nombre de cada cara",       "Memoria"),
    GameDefinition("mental_calc",          "Cálculo Mental",          CognitiveDomain.PROCESSING_SPEED, "➕", "Operaciones aritméticas",            "Velocidad"),
    GameDefinition("prospective_memory",   "Memoria Prospectiva",     CognitiveDomain.MEMORY,           "⏰", "Recuerda hacer algo después",        "Memoria"),
    GameDefinition("reading_comprehension","Lectura y Comprensión",   CognitiveDomain.LANGUAGE,         "📖", "Lee y responde preguntas",           "Lenguaje")
)

/** Icono (emoji) por dominio cognitivo para los chips de filtro. */
fun CognitiveDomain.catalogEmoji(): String = when(this) {
    CognitiveDomain.MEMORY           -> "🧠"
    CognitiveDomain.ATTENTION        -> "👁️"
    CognitiveDomain.EXECUTIVE        -> "⚙️"
    CognitiveDomain.LANGUAGE         -> "💬"
    CognitiveDomain.ORIENTATION      -> "🧭"
    CognitiveDomain.PROCESSING_SPEED -> "⚡"
}

fun CognitiveDomain.catalogLabel(): String = when(this) {
    CognitiveDomain.MEMORY           -> "Memoria"
    CognitiveDomain.ATTENTION        -> "Atención"
    CognitiveDomain.EXECUTIVE        -> "Ejecutivo"
    CognitiveDomain.LANGUAGE         -> "Lenguaje"
    CognitiveDomain.ORIENTATION      -> "Orientación"
    CognitiveDomain.PROCESSING_SPEED -> "Velocidad"
}
