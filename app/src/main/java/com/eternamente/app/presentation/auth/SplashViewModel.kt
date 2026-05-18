package com.eternamente.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.core.Result
import com.eternamente.app.data.local.preferences.UserPreferences
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    sealed class Destination {
        object Idle       : Destination()
        object Register   : Destination()
        object Login      : Destination()
        object Onboarding : Destination()
        object Dashboard  : Destination()
    }

    private val _destination = MutableStateFlow<Destination>(Destination.Idle)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            delay(1_200L)
            userPreferencesRepository.preferences.collect { p ->
                _destination.value = resolveDestination(p)
                return@collect
            }
        }
    }

    private suspend fun resolveDestination(p: UserPreferences): Destination {
        if (p.currentUserId == null) {
            Timber.d("Auth: Splash → Register (sin usuario en DataStore)")
            return Destination.Register
        }
        if (!p.onboardingCompleted) {
            Timber.d("Auth: Splash → Onboarding (perfil incompleto)")
            return Destination.Onboarding
        }

        // Verificar que el usuario existe en Room (puede haberse perdido por migration reset)
        val userExistsInRoom = runCatching {
            when (val r = userRepository.getUserById(p.currentUserId)) {
                is Result.Success -> r.data != null
                is Result.Error   -> false
            }
        }.getOrDefault(false)

        return when {
            !userExistsInRoom -> {
                // Inconsistencia DataStore↔Room: limpiar y forzar re-registro
                Timber.w("Auth: Splash → Register (inconsistencia DataStore↔Room)")
                userPreferencesRepository.updateIsLoggedIn(false)
                userPreferencesRepository.updateCurrentUserId(null)
                Destination.Register
            }
            p.isLoggedIn -> {
                Timber.d("Auth: Splash → Dashboard (sesión activa)")
                Destination.Dashboard
            }
            else -> {
                Timber.d("Auth: Splash → Login (sesión no activa)")
                Destination.Login
            }
        }
    }
}
