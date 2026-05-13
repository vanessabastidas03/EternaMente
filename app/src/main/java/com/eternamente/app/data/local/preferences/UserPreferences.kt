package com.eternamente.app.data.local.preferences

/**
 * Preferencias del usuario almacenadas en DataStore (cifrado en reposo vía
 * [androidx.datastore.preferences.core.Preferences]).
 *
 * @property fontScale         Factor de escala tipográfica (0.85, 1.0, 1.15, 1.30).
 * @property highContrast      Activa paleta de alto contraste en el tema.
 * @property hapticFeedback    Vibración al responder en los juegos.
 * @property darkMode          Fuerza el modo oscuro independientemente del sistema.
 * @property onboardingCompleted `true` una vez que el usuario completa el flujo inicial.
 */
data class UserPreferences(
    val fontScale: Float             = 1.0f,
    val highContrast: Boolean        = false,
    val hapticFeedback: Boolean      = true,
    val darkMode: Boolean            = false,
    val onboardingCompleted: Boolean = false,
    /** UUID del usuario actualmente autenticado; `null` = no hay sesión activa. */
    val currentUserId: String?       = null,
    /**
     * `true` cuando el usuario completó login o onboarding exitosamente.
     * Permite al Splash ir directamente a Dashboard sin pedir PIN.
     * Se pone a `false` en logout explícito.
     */
    val isLoggedIn: Boolean          = false
)
