package com.eternamente.app.domain.model

/**
 * Core user entity representing a registered participant in the EternaMente system.
 *
 * This is a pure Kotlin data class with no Android or Room annotations.
 * Persistence mapping is handled by the data layer.
 *
 * @property id Unique identifier (UUID v4) assigned at registration.
 * @property name Full display name as entered by the user or caregiver.
 * @property age Current age in years; used for normative score adjustment.
 * @property educationYears Total years of formal education completed.
 *   Higher education correlates with cognitive reserve and affects score interpretation.
 * @property gender Self-reported gender identifier (free-form string, not an enum,
 *   to accommodate diverse responses without imposing categories).
 * @property createdAt Account creation timestamp in epoch milliseconds (UTC).
 * @property consentGivenAt Timestamp of informed-consent acceptance in epoch milliseconds;
 *   `null` if the user has not yet accepted the consent form.
 *   No cognitive data may be collected while this is `null`.
 */
data class User(
    val id: String,
    val name: String,
    val age: Int,
    val educationYears: Int,
    val gender: String,
    val createdAt: Long,
    val consentGivenAt: Long?
) {

    /** Returns `true` if the user has explicitly accepted the informed-consent form. */
    val hasConsent: Boolean get() = consentGivenAt != null

    /**
     * Returns a redacted copy of this user suitable for logging and analytics,
     * with the [name] replaced by its initials and [id] truncated to 8 characters.
     */
    fun redacted(): User = copy(
        id   = id.take(8) + "…",
        name = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.joinToString(".")
    )
}
