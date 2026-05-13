package com.eternamente.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val darkMode: Boolean     = false,
    val highContrast: Boolean = false,
    val hapticFeedback: Boolean = true,
    val isLoggingOut: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val state: StateFlow<SettingsState> = userPreferencesRepository.preferences
        .map { p ->
            SettingsState(
                darkMode      = p.darkMode,
                highContrast  = p.highContrast,
                hapticFeedback = p.hapticFeedback
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    // One-shot event: UI collects this to navigate away after logout completes
    private val _logoutComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutComplete: SharedFlow<Unit> = _logoutComplete.asSharedFlow()

    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            _loggingOut.value = true
            logoutUseCase()
            _loggingOut.value = false
            _logoutComplete.emit(Unit)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.updateDarkMode(enabled) }
    }

    fun toggleHighContrast(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.updateHighContrast(enabled) }
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.updateHapticFeedback(enabled) }
    }
}
