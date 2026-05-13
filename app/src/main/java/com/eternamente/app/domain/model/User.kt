package com.eternamente.app.domain.model

/**
 * Core user entity representing a registered participant in the EternaMente system.
 *
 * Pure Kotlin data class — no Android or Room annotations.
 *
 * @property id            UUID v4 asignado en el registro.
 * @property email         Correo electrónico (en minúsculas, sin espacios). Usado como
 *                         identificador de login junto con el PIN.
 * @property name          Nombre completo tal como lo ingresó el usuario.
 * @property age           Edad en años; 0 hasta que se complete el onboarding.
 * @property educationYears Años de educación formal; 0 hasta el onboarding.
 * @property gender        Identidad de género en texto libre; vacío hasta el onboarding.
 * @property createdAt     Timestamp de creación de cuenta en epoch-ms (UTC).
 * @property consentGivenAt Timestamp de aceptación del consentimiento informado; `null`
 *                         si aún no se ha aceptado. Sin datos cognitivos sin consentimiento.
 */
data class User(
    val id: String,
    val email: String,
    val name: String,
    val age: Int,
    val educationYears: Int,
    val gender: String,
    val createdAt: Long,
    val consentGivenAt: Long?
) {
    /** `true` si el usuario ha aceptado el consentimiento informado. */
    val hasConsent: Boolean get() = consentGivenAt != null

    /** `true` si el perfil demográfico está completo (post-onboarding). */
    val hasProfile: Boolean get() = age > 0 && educationYears > 0

    /**
     * Versión redactada apta para logs y analítica:
     * - [id] truncado a 8 caracteres
     * - [name] reemplazado por iniciales
     * - [email] con dominio enmascarado
     */
    fun redacted(): User = copy(
        id    = id.take(8) + "…",
        name  = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.joinToString("."),
        email = email.substringBefore("@").take(3) + "***@" + email.substringAfter("@")
    )
}
