package com.eternamente.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferences
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.repository.GamificationRepository
import com.eternamente.app.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel del flujo de onboarding de EternaMente.
 *
 * Expone un único [StateFlow]<[OnboardingState]> que los composables observan
 * de forma reactiva. Toda la lógica de negocio reside aquí; los composables
 * son stateless y reciben callbacks.
 *
 * **Flujo de pasos:**
 * Welcome → Profile → Consent → Accessibility → [completeOnboarding]
 *
 * **Al completar el onboarding:**
 * 1. Crea el [User] en Room con los datos del perfil.
 * 2. Registra el timestamp de consentimiento.
 * 3. Inicializa el perfil de gamificación.
 * 4. Persiste las preferencias de accesibilidad en DataStore.
 * 5. Marca `isComplete = true` para que el composable navegue a Dashboard.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val gamificationRepository: GamificationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    // ── Navegación entre pasos ────────────────────────────────────────────────

    fun nextStep() {
        val current = _state.value.currentStep
        when (current) {
            OnboardingStep.Welcome -> {
                _state.update { it.copy(currentStep = OnboardingStep.Profile) }
            }
            OnboardingStep.Profile -> {
                if (validateProfile()) {
                    _state.update { it.copy(currentStep = OnboardingStep.Consent) }
                }
            }
            OnboardingStep.Consent -> {
                if (_state.value.consentForm.canAccept) {
                    _state.update { it.copy(currentStep = OnboardingStep.Accessibility) }
                }
            }
            OnboardingStep.Accessibility -> completeOnboarding()
        }
    }

    fun previousStep() {
        val current = _state.value.currentStep
        val previous = when (current) {
            OnboardingStep.Profile      -> OnboardingStep.Welcome
            OnboardingStep.Consent      -> OnboardingStep.Profile
            OnboardingStep.Accessibility -> OnboardingStep.Consent
            OnboardingStep.Welcome      -> return  // No hay paso anterior
        }
        _state.update { it.copy(currentStep = previous, error = null) }
    }

    // ── Paso 2: Perfil ────────────────────────────────────────────────────────

    fun onNameChanged(name: String) {
        val error = validateName(name)
        _state.update { it.copy(profileForm = it.profileForm.copy(name = name, nameError = error)) }
    }

    fun onAgeChanged(age: Int) {
        _state.update { it.copy(profileForm = it.profileForm.copy(age = age.coerceIn(60, 100))) }
    }

    fun onEducationChanged(level: EducationLevel) {
        _state.update { it.copy(profileForm = it.profileForm.copy(educationLevel = level)) }
    }

    fun onGenderChanged(gender: Gender) {
        _state.update { it.copy(profileForm = it.profileForm.copy(gender = gender)) }
    }

    // ── Paso 3: Consentimiento ────────────────────────────────────────────────

    fun onScrolledToConsentEnd() {
        _state.update { it.copy(consentForm = it.consentForm.copy(scrolledToEnd = true)) }
    }

    fun onConsentCheckboxChanged(checked: Boolean) {
        _state.update { it.copy(consentForm = it.consentForm.copy(checkboxChecked = checked)) }
    }

    // ── Paso 4: Accesibilidad ─────────────────────────────────────────────────

    fun onFontScaleChanged(scale: FontScale) {
        _state.update { it.copy(accessibilityForm = it.accessibilityForm.copy(fontScale = scale)) }
    }

    /**
     * Cambia alto contraste y lo persiste INMEDIATAMENTE en DataStore.
     *
     * La cadena de reactividad garantiza el efecto visual instantáneo:
     * DataStore.edit → Flow emite → MainViewModel.StateFlow emite →
     * collectAsState invalida → EternaMenteTheme elige HCScheme → Compose redibuja.
     * Todo ocurre dentro del mismo frame o en el siguiente, sin recrear la Activity.
     */
    fun onHighContrastChanged(enabled: Boolean) {
        _state.update { it.copy(accessibilityForm = it.accessibilityForm.copy(highContrast = enabled)) }
        viewModelScope.launch { userPreferencesRepository.updateHighContrast(enabled) }
    }

    /** Cambia vibración y la persiste inmediatamente en DataStore. */
    fun onHapticFeedbackChanged(enabled: Boolean) {
        _state.update { it.copy(accessibilityForm = it.accessibilityForm.copy(hapticFeedback = enabled)) }
        viewModelScope.launch { userPreferencesRepository.updateHapticFeedback(enabled) }
    }

    /**
     * Cambia modo oscuro y lo persiste INMEDIATAMENTE en DataStore.
     * Misma cadena reactiva que [onHighContrastChanged].
     */
    fun onDarkModeChanged(enabled: Boolean) {
        _state.update { it.copy(accessibilityForm = it.accessibilityForm.copy(darkMode = enabled)) }
        viewModelScope.launch { userPreferencesRepository.updateDarkMode(enabled) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // ── Finalización ─────────────────────────────────────────────────────────

    private fun completeOnboarding() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            runCatching {
                val form       = _state.value.profileForm
                val accessForm = _state.value.accessibilityForm
                val now        = System.currentTimeMillis()

                // El usuario fue creado en RegisterScreen — recuperar su ID
                val userId = userPreferencesRepository.getCurrentUserId()
                    ?: throw IllegalStateException("No hay usuario registrado activo")

                // 1 — Actualizar perfil demográfico del usuario existente
                val existingUser = userRepository.getUserById(userId).getOrThrow()
                val updatedUser  = existingUser.copy(
                    age            = form.age,
                    educationYears = form.educationLevel.years,
                    gender         = form.gender.displayName,
                    consentGivenAt = now
                )
                userRepository.updateUser(updatedUser).getOrThrow()

                // 2 — Registrar timestamp de consentimiento (campo consent_given_at)
                userRepository.recordConsent(userId, now).getOrThrow()

                // 3 — Persistir preferencias de accesibilidad + marcar onboarding completo
                userPreferencesRepository.savePreferences(
                    UserPreferences(
                        fontScale           = accessForm.fontScale.scale,
                        highContrast        = accessForm.highContrast,
                        hapticFeedback      = accessForm.hapticFeedback,
                        darkMode            = accessForm.darkMode,
                        onboardingCompleted = true,
                        currentUserId       = userId,
                        // Marcar sesión activa → el próximo inicio va directo a Dashboard
                        isLoggedIn          = true
                    )
                )
            }.onSuccess {
                _state.update { it.copy(isLoading = false, isComplete = true) }
            }.onFailure { exception ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "No se pudo completar el registro. Verifica tu conexión."
                    )
                }
            }
        }
    }

    // ── Validaciones ──────────────────────────────────────────────────────────

    private fun validateProfile(): Boolean {
        val form  = _state.value.profileForm
        val error = validateName(form.name)
        _state.update { it.copy(profileForm = it.profileForm.copy(nameError = error)) }
        return error == null && form.name.isNotBlank()
    }

    private fun validateName(name: String): String? = when {
        name.isBlank() ->
            "El nombre no puede estar vacío"
        !name.matches(Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ ]+\$")) ->
            "Solo se permiten letras y espacios"
        name.trim().length < 2 ->
            "El nombre debe tener al menos 2 letras"
        else -> null
    }

    // ── Helper: getOrThrow para Result<T> ─────────────────────────────────────

    private fun <T> com.eternamente.app.core.Result<T>.getOrThrow(): T = when (this) {
        is com.eternamente.app.core.Result.Success -> data
        is com.eternamente.app.core.Result.Error   -> throw exception
    }
}
