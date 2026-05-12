package com.eternamente.app.domain.model

/**
 * Classifies the purpose and scope of a [CognitiveSession].
 *
 * The type determines which mini-games are included, how many domains
 * are evaluated, and how results are interpreted by the ML pipeline.
 */
enum class SessionType {

    /**
     * Full-length initial assessment administered once per user.
     * Covers all six [CognitiveDomain]s and establishes the [CognitiveBaseline].
     */
    BASELINE,

    /**
     * Short daily check-in targeting two or three domains.
     * Optimized for engagement; duration typically under ten minutes.
     */
    DAILY,

    /**
     * Comprehensive weekly review covering all six [CognitiveDomain]s.
     * Results feed into the ML prediction pipeline alongside BASELINE data.
     */
    WEEKLY_FULL
}
