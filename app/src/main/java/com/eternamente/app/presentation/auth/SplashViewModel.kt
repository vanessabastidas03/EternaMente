package com.eternamente.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de la pantalla de splash.
 *
 * Determina el destino inicial leyendo [UserPreferencesRepository] tras
 * un breve delay para mostrar la animación de splash.
 *
 * Destinos posibles:
 * - [Destination.Register] → primera vez (sin cuenta)
 * - [Destination.Login]    → cuenta existente, onboarding completo
 * - [Destination.Onboarding] → cuenta creada pero onboarding pendiente
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    sealed class Destination {
        object Idle        : Destination()
        object Register    : Destination()
        object Login       : Destination()
        object Onboarding  : Destination()
    }

    private val _destination = MutableStateFlow<Destination>(Destination.Idle)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            delay(1_500L)  // Splash display delay
            val prefs = userPreferencesRepository.preferences
            prefs.collect { p ->
                _destination.value = when {
                    p.currentUserId == null    -> Destination.Register
                    !p.onboardingCompleted     -> Destination.Onboarding
                    else                       -> Destination.Login
                }
                // Emitir solo una vez
                return@collect
            }
        }
    }
}
