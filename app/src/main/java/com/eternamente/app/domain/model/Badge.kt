package com.eternamente.app.domain.model

/**
 * Achievement badges that can be unlocked through consistent engagement and performance.
 *
 * Each badge has a localization-ready [displayName] and [description].
 * The actual UI strings should be resolved through the presentation layer;
 * these values serve as fallback identifiers.
 *
 * @property displayName Short human-readable badge title.
 * @property description One-sentence explanation of the unlock condition.
 */
enum class Badge(
    val displayName: String,
    val description: String
) {

    /**
     * Awarded when the user completes their very first cognitive session.
     * Intended to reduce early drop-off by celebrating the initial commitment.
     */
    FIRST_STEP(
        displayName   = "Primer paso",
        description   = "Completaste tu primera sesión cognitiva"
    ),

    /**
     * Awarded for maintaining a 7-consecutive-day session streak.
     */
    WEEK_WARRIOR(
        displayName   = "Guerrero semanal",
        description   = "7 días consecutivos de entrenamiento"
    ),

    /**
     * Awarded for maintaining a 30-consecutive-day session streak.
     */
    CONSISTENCY_MASTER(
        displayName   = "Maestro de consistencia",
        description   = "30 días consecutivos de entrenamiento"
    ),

    /**
     * Awarded for achieving 100% accuracy in any memory mini-game.
     */
    MEMORY_ACE(
        displayName   = "As de la memoria",
        description   = "Puntaje perfecto en un juego de memoria"
    ),

    /**
     * Awarded for achieving 100% accuracy in any attention mini-game.
     */
    ATTENTION_CHAMPION(
        displayName   = "Campeón de atención",
        description   = "Puntaje perfecto en un juego de atención"
    ),

    /**
     * Awarded for completing at least one game in every [CognitiveDomain].
     */
    DOMAIN_EXPLORER(
        displayName   = "Explorador cognitivo",
        description   = "Entrenaste todos los dominios cognitivos"
    ),

    /**
     * Awarded for reaching the maximum adaptive difficulty level in any mini-game.
     */
    LEVEL_MAX(
        displayName   = "Nivel máximo",
        description   = "Alcanzaste el nivel de dificultad máximo"
    ),

    /**
     * Awarded for completing a full [SessionType.WEEKLY_FULL] session without interruption.
     */
    FULL_SPRINT(
        displayName   = "Sprint completo",
        description   = "Completaste una sesión semanal completa"
    ),

    /**
     * Awarded when the user returns after a gap of seven or more days.
     * Designed to reward re-engagement rather than penalize absences.
     */
    COMEBACK(
        displayName   = "De regreso",
        description   = "Volviste después de una pausa de 7 días o más"
    ),

    /**
     * Awarded to users who registered during the platform's early-access period.
     */
    EARLY_ADOPTER(
        displayName   = "Pionero",
        description   = "Uno de los primeros usuarios de EternaMente"
    ),

    /** Reaction time < 500 ms in Flash de Colores. */
    SPEED_DEMON(
        displayName   = "Velocidad relámpago",
        description   = "Reaccionaste en menos de 500 ms en Flash de Colores"
    ),

    /** 14 consecutive days of sessions. */
    CONSISTENT(
        displayName   = "Constancia de campeón",
        description   = "14 días consecutivos de entrenamiento cognitivo"
    ),

    /** First cognitive report generated. */
    FIRST_REPORT(
        displayName   = "Primera mirada",
        description   = "Generaste tu primer reporte de progreso cognitivo"
    )
}
