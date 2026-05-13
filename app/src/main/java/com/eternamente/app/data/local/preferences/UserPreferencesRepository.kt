package com.eternamente.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Singleton de DataStore a nivel de fichero — garantiza una única instancia
private val Context.userPrefsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "eternamente_user_prefs")

/**
 * Repositorio de preferencias del usuario respaldado por DataStore.
 *
 * Todas las operaciones de escritura son suspending; la lectura expone un
 * [Flow] reactivo que emite cada vez que cambia alguna preferencia.
 *
 * En caso de corrupción del archivo de preferencias, emite [UserPreferences]
 * con los valores por defecto en lugar de lanzar una excepción.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val KEY_FONT_SCALE            = floatPreferencesKey("font_scale")
        val KEY_HIGH_CONTRAST         = booleanPreferencesKey("high_contrast")
        val KEY_HAPTIC_FEEDBACK       = booleanPreferencesKey("haptic_feedback")
        val KEY_DARK_MODE             = booleanPreferencesKey("dark_mode")
        val KEY_ONBOARDING_COMPLETED  = booleanPreferencesKey("onboarding_completed")
    }

    /** Flujo de preferencias actuales; emite al iniciarse y en cada cambio. */
    val preferences: Flow<UserPreferences> = context.userPrefsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            UserPreferences(
                fontScale           = prefs[KEY_FONT_SCALE]           ?: 1.0f,
                highContrast        = prefs[KEY_HIGH_CONTRAST]        ?: false,
                hapticFeedback      = prefs[KEY_HAPTIC_FEEDBACK]      ?: true,
                darkMode            = prefs[KEY_DARK_MODE]            ?: false,
                onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false
            )
        }

    /**
     * Persiste el objeto [UserPreferences] completo de forma atómica.
     *
     * Llamar desde un coroutine — no bloquea el hilo principal.
     */
    suspend fun savePreferences(preferences: UserPreferences) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_FONT_SCALE]           = preferences.fontScale
            prefs[KEY_HIGH_CONTRAST]        = preferences.highContrast
            prefs[KEY_HAPTIC_FEEDBACK]      = preferences.hapticFeedback
            prefs[KEY_DARK_MODE]            = preferences.darkMode
            prefs[KEY_ONBOARDING_COMPLETED] = preferences.onboardingCompleted
        }
    }

    /** Marca el onboarding como completado sin modificar otras preferencias. */
    suspend fun markOnboardingCompleted() {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    /** Actualiza solo el factor de escala tipográfica. */
    suspend fun updateFontScale(scale: Float) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_FONT_SCALE] = scale
        }
    }

    /**
     * Actualiza solo el modo oscuro.
     *
     * Se llama inmediatamente al cambiar el switch en [AccessibilityStep] para
     * que [MainActivity] reciba el nuevo valor del Flow y recomponga el tema
     * sin esperar a que termine el onboarding.
     */
    suspend fun updateDarkMode(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }

    /**
     * Actualiza solo el alto contraste.
     *
     * Mismo patrón que [updateDarkMode]: escritura inmediata → Flow emite →
     * [MainActivity] recompone → [EternaMenteTheme] elige [HighContrastLightColorScheme]
     * o [HighContrastDarkColorScheme] sin reiniciar la Activity.
     */
    suspend fun updateHighContrast(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_HIGH_CONTRAST] = enabled
        }
    }

    /** Actualiza solo el haptic feedback. */
    suspend fun updateHapticFeedback(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_HAPTIC_FEEDBACK] = enabled
        }
    }
}
