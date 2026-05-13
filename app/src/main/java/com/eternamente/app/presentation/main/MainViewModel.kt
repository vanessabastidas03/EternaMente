package com.eternamente.app.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferences
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel de la Activity raíz.
 *
 * Su única responsabilidad es convertir el [Flow]<[UserPreferences]> de DataStore
 * en un [StateFlow] con `SharingStarted.Eagerly` para que el valor esté disponible
 * en el primer frame de composición, sin flash de tema incorrecto.
 *
 * **Por qué `Eagerly` y no `WhileSubscribed`:**
 * Con `WhileSubscribed`, DataStore comenzaría a leer solo cuando haya un
 * suscriptor activo. En la composición inicial, esto puede causar que la
 * Activity arranque con los valores por defecto (`darkMode=false`) durante
 * un frame antes de que llegue el primer emisión del disco. Con `Eagerly`,
 * la lectura comienza inmediatamente al crear el ViewModel (antes de `setContent`),
 * eliminando el flash.
 *
 * **Persistencia al reiniciar:**
 * DataStore persiste las preferencias en disco. Al relanzar la app, el primer
 * valor emitido por el Flow ya contiene las preferencias guardadas.
 *
 * @property preferences [StateFlow] de [UserPreferences] observable desde [MainActivity].
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = userPreferencesRepository.preferences
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        )
}
