package com.eternamente.app.domain.model

/**
 * Discretized risk level derived from a [MlPrediction.riskScore].
 *
 * Used by the UI and notification system to determine the appropriate
 * communication tone and recommended actions for the caregiver or clinician.
 */
enum class AlertLevel {

    /**
     * Risk score below the watch threshold.
     * No actionable concern; continue routine monitoring.
     */
    NORMAL,

    /**
     * Risk score in the intermediate range.
     * A downward trend has been detected; increased session frequency is suggested.
     */
    WATCH,

    /**
     * Risk score above the clinical concern threshold.
     * A professional evaluation is recommended; caregiver notification is triggered.
     */
    ALERT
}
