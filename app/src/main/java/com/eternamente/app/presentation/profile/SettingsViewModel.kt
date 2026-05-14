package com.eternamente.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.core.Result
import com.eternamente.app.core.notifications.EternaNotificationManager
import com.eternamente.app.core.notifications.NotificationScheduler
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.repository.UserRepository
import com.eternamente.app.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class SettingsState(
    val darkMode:               Boolean = false,
    val highContrast:           Boolean = false,
    val hapticFeedback:         Boolean = true,
    val isLoggingOut:           Boolean = false,
    val notificationsEnabled:   Boolean = true,
    val notificationHour:       Int     = 9,
    val notificationMinute:     Int     = 0,
    val canScheduleExactAlarms: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val logoutUseCase:             LogoutUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationScheduler:     NotificationScheduler,
    private val notificationManager:       EternaNotificationManager,
    private val userRepository:            UserRepository
) : ViewModel() {

    val state: StateFlow<SettingsState> = userPreferencesRepository.preferences
        .map { p ->
            SettingsState(
                darkMode               = p.darkMode,
                highContrast           = p.highContrast,
                hapticFeedback         = p.hapticFeedback,
                notificationsEnabled   = p.notificationsEnabled,
                notificationHour       = p.notificationHour,
                notificationMinute     = p.notificationMinute,
                canScheduleExactAlarms = notificationScheduler.canScheduleExactAlarms()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _logoutComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutComplete: SharedFlow<Unit> = _logoutComplete.asSharedFlow()

    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    // ── Appearance ────────────────────────────────────────────────────────────

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.updateDarkMode(enabled) }
    }

    fun toggleHighContrast(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.updateHighContrast(enabled) }
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.updateHapticFeedback(enabled) }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateNotificationsEnabled(enabled)
            if (enabled) {
                scheduleWithCurrentPrefs()
            } else {
                notificationScheduler.cancelDaily()
                Timber.i("SettingsVM: daily alarm cancelled by user")
            }
        }
    }

    fun updateNotificationTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateNotificationTime(hour, minute)
            scheduleWithCurrentPrefs(overrideHour = hour, overrideMinute = minute)
        }
    }

    fun openExactAlarmSettings() {
        notificationScheduler.openExactAlarmSettings()
    }

    /** Fires an immediate test notification — bypasses AlarmManager. */
    fun sendTestNotification() {
        viewModelScope.launch {
            val userName = resolveUserName()
            Timber.i("SettingsVM: sending TEST notification for user='$userName'")
            notificationManager.showDailyReminder(userName = userName, currentStreak = 3)
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun logout() {
        viewModelScope.launch {
            _loggingOut.value = true
            logoutUseCase()
            notificationScheduler.cancelDaily()
            _loggingOut.value = false
            _logoutComplete.emit(Unit)
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun scheduleWithCurrentPrefs(
        overrideHour: Int? = null,
        overrideMinute: Int? = null
    ) {
        // Use first() — NOT stateIn().value which returns null before DataStore emits
        val prefs    = userPreferencesRepository.preferences.first()
        val hour     = overrideHour   ?: prefs.notificationHour
        val minute   = overrideMinute ?: prefs.notificationMinute
        val userName = resolveUserName()

        Timber.i("SettingsVM: scheduling daily alarm at %02d:%02d for user='$userName'".format(hour, minute))
        notificationScheduler.scheduleDaily(hour = hour, minute = minute, userName = userName)
        userPreferencesRepository.updateNotificationUserName(userName)
    }

    private suspend fun resolveUserName(): String {
        val userId = userPreferencesRepository.getCurrentUserId() ?: return "amigo"
        return withContext(Dispatchers.IO) {
            when (val r = userRepository.getUserById(userId)) {
                is Result.Success -> r.data.name.split(" ").firstOrNull() ?: "amigo"
                is Result.Error   -> "amigo"
            }
        }
    }
}
