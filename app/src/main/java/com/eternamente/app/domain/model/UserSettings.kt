package com.eternamente.app.domain.model

/**
 * Preferencias de uso de la app vinculadas a la cuenta del usuario.
 *
 * A diferencia de [com.eternamente.app.data.local.preferences.UserPreferences]
 * (almacenadas en DataStore, solo locales), estas configuraciones viven en Room
 * junto al perfil del usuario y se restauran al iniciar sesión en un dispositivo
 * distinto o tras reinstalar la app.
 *
 * @property userId                UUID del [User] propietario.
 * @property notificationsEnabled  Activa los recordatorios diarios de sesión.
 * @property notificationHour      Hora del recordatorio (0–23).
 * @property notificationMinute    Minuto del recordatorio (0–59).
 * @property sessionFrequencyPerWeek Sesiones objetivo por semana (1–7).
 * @property language              Código de idioma BCP-47 (ej. `"es"`, `"en"`).
 */
data class UserSettings(
    val userId: String,
    val notificationsEnabled: Boolean = true,
    val notificationHour: Int         = 9,
    val notificationMinute: Int       = 0,
    val sessionFrequencyPerWeek: Int  = 5,
    val language: String              = "es"
)
