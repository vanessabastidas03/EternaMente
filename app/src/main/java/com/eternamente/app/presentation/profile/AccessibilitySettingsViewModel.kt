package com.eternamente.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.presentation.onboarding.FontScale
import com.eternamente.app.presentation.onboarding.FontScale.LARGE
import com.eternamente.app.presentation.onboarding.FontScale.NORMAL
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccessibilitySettingsState(
    val highContrast:   Boolean    = false,
    val darkMode:       Boolean    = false,
    val fontScale:      Float      = 1f,
    val largeText:      Boolean    = false,   // fontScale >= 1.15f
    val hapticFeedback: Boolean    = true,
    val reducedMotion:  Boolean    = false,
    val talkBackMode:   Boolean    = false
)

@HiltViewModel
class AccessibilitySettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val state: StateFlow<AccessibilitySettingsState> =
        preferencesRepository.preferences
            .map { prefs ->
                AccessibilitySettingsState(
                    highContrast   = prefs.highContrast,
                    darkMode       = prefs.darkMode,
                    fontScale      = prefs.fontScale,
                    largeText      = prefs.fontScale >= 1.15f,
                    hapticFeedback = prefs.hapticFeedback,
                    reducedMotion  = prefs.reducedMotion,
                    talkBackMode   = prefs.talkBackMode
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccessibilitySettingsState())

    fun toggleHighContrast(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateHighContrast(enabled) }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateDarkMode(enabled) }
    }

    fun toggleLargeText(enabled: Boolean) {
        viewModelScope.launch {
            val scale = if (enabled) FontScale.LARGE.scale else FontScale.NORMAL.scale
            preferencesRepository.updateFontScale(scale)
        }
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateHapticFeedback(enabled) }
    }

    fun toggleReducedMotion(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateReducedMotion(enabled) }
    }

    fun toggleTalkBackMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateTalkBackMode(enabled) }
    }
}
