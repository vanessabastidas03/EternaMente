package com.eternamente.app.domain.model

/**
 * Cognitive domains measured by EternaMente's mini-game battery.
 *
 * Each domain maps to one or more games and corresponds to a distinct
 * neuropsychological construct used in mild cognitive impairment (MCI) screening.
 */
enum class CognitiveDomain {

    /** Short-term and working memory (e.g., digit span, word recall). */
    MEMORY,

    /** Sustained and selective attention (e.g., continuous performance, Stroop). */
    ATTENTION,

    /** Executive function and cognitive flexibility (e.g., trail-making, task switching). */
    EXECUTIVE,

    /** Verbal fluency and naming ability (e.g., category fluency, picture naming). */
    LANGUAGE,

    /** Temporal and spatial orientation (e.g., date/place identification). */
    ORIENTATION,

    /** Psychomotor speed and information processing rate (e.g., simple reaction time). */
    PROCESSING_SPEED
}
