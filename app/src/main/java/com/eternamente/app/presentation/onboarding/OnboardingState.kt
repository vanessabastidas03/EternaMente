package com.eternamente.app.presentation.onboarding

// ══════════════════════════════════════════════════════════════════════════════
// Enums de formulario — solo existen en la capa de presentación.
// Al completar el onboarding se mapean al dominio (User, UserPreferences).
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Nivel de escolaridad seleccionable en el paso de perfil.
 *
 * @property displayName Texto visible al usuario.
 * @property years       Años de educación formal aproximados (para normalización cognitiva).
 */
enum class EducationLevel(val displayName: String, val years: Int) {
    PRIMARY("Primaria", 6),
    SECONDARY("Secundaria", 12),
    TECHNICAL("Técnico/Tecnológico", 14),
    UNIVERSITY("Universitario", 16),
    POSTGRADUATE("Posgrado", 18)
}

/**
 * Identidad de género (diseñada para ser inclusiva sin ser exhaustiva).
 *
 * @property displayName Texto visible al usuario.
 */
enum class Gender(val displayName: String) {
    WOMAN("Mujer"),
    MAN("Hombre"),
    PREFER_NOT_SAY("Prefiero no decir")
}

/**
 * Escala de tamaño de fuente seleccionable en el paso de accesibilidad.
 *
 * @property label Etiqueta visible bajo cada posición del slider.
 * @property scale Factor multiplicador aplicado al tamaño base de 18 sp.
 */
enum class FontScale(val label: String, val scale: Float) {
    SMALL("Pequeño", 0.85f),
    NORMAL("Normal",  1.00f),
    LARGE("Grande",   1.15f),
    XLARGE("Muy grande", 1.30f)
}

// ══════════════════════════════════════════════════════════════════════════════
// Steps del onboarding
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Clase sellada que representa cada paso del flujo de onboarding.
 *
 * @property index    Posición base-0 del paso (usada para el indicador de progreso).
 * @property title    Título mostrado en el TopBar de los pasos 2, 3 y 4.
 */
sealed class OnboardingStep(val index: Int, val title: String) {

    /** Paso 1 — Bienvenida. El botón atrás del sistema está deshabilitado. */
    object Welcome      : OnboardingStep(0, "Bienvenida")

    /** Paso 2 — Creación del perfil demográfico. */
    object Profile      : OnboardingStep(1, "Tu perfil")

    /** Paso 3 — Consentimiento informado (RGPD + no-diagnóstico). */
    object Consent      : OnboardingStep(2, "Consentimiento")

    /** Paso 4 — Preferencias de accesibilidad con vista previa en tiempo real. */
    object Accessibility : OnboardingStep(3, "Accesibilidad")

    companion object {
        const val TOTAL_STEPS = 4
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Estados de cada paso
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Estado del formulario de perfil del paso 2.
 *
 * La validación ocurre en el ViewModel; los campos de error son `null` si el
 * campo es válido o no ha sido tocado aún.
 */
data class ProfileFormState(
    val name: String              = "",
    val nameError: String?        = null,
    val age: Int                  = 70,
    val educationLevel: EducationLevel = EducationLevel.PRIMARY,
    val gender: Gender            = Gender.PREFER_NOT_SAY
) {
    /** `true` cuando todos los campos superan validación. */
    val isValid: Boolean get() = name.isNotBlank() && nameError == null
}

/**
 * Estado del paso de consentimiento informado.
 *
 * El botón "Acepto" se habilita solo cuando AMBAS condiciones son `true`.
 */
data class ConsentFormState(
    val scrolledToEnd: Boolean   = false,
    val checkboxChecked: Boolean = false
) {
    /** `true` cuando el usuario puede confirmar el consentimiento. */
    val canAccept: Boolean get() = scrolledToEnd && checkboxChecked
}

/**
 * Preferencias de accesibilidad seleccionadas en el paso 4.
 * Se persisten en DataStore al completar el onboarding.
 */
data class AccessibilityFormState(
    val fontScale: FontScale   = FontScale.NORMAL,
    val highContrast: Boolean  = false,
    val hapticFeedback: Boolean = true,
    val darkMode: Boolean      = false
)

// ══════════════════════════════════════════════════════════════════════════════
// Estado global
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Estado completo del flujo de onboarding expuesto por [OnboardingViewModel].
 *
 * Es el único [StateFlow] que los composables observan — sin lógica de
 * UI embebida en el ViewModel.
 */
data class OnboardingState(
    val currentStep: OnboardingStep     = OnboardingStep.Welcome,
    val profileForm: ProfileFormState   = ProfileFormState(),
    val consentForm: ConsentFormState   = ConsentFormState(),
    val accessibilityForm: AccessibilityFormState = AccessibilityFormState(),
    val isLoading: Boolean              = false,
    val isComplete: Boolean             = false,
    val error: String?                  = null
)
