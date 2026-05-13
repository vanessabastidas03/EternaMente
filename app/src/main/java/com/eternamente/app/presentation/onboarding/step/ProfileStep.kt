package com.eternamente.app.presentation.onboarding.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.component.EternaTextField
import com.eternamente.app.presentation.onboarding.EducationLevel
import com.eternamente.app.presentation.onboarding.Gender
import com.eternamente.app.presentation.onboarding.ProfileFormState
import kotlin.math.roundToInt

/**
 * Paso 2 del onboarding — Creación del perfil demográfico.
 *
 * **Campos y validación:**
 * - **Nombre**: Solo letras y espacios, mínimo 2 caracteres. Error en tiempo real.
 * - **Edad**: Slider continuo 60–100 con valor numérico visible.
 * - **Escolaridad**: Dropdown con 5 opciones (accesible via teclado y TalkBack).
 * - **Género**: RadioGroup con 3 opciones; la semántica del [selectable] comunica
 *   el estado a TalkBack sin duplicar texto.
 *
 * El botón "Continuar" solo se habilita cuando la validación del perfil pasa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileStep(
    profileForm: ProfileFormState,
    onNameChanged: (String) -> Unit,
    onAgeChanged: (Int) -> Unit,
    onEducationChanged: (EducationLevel) -> Unit,
    onGenderChanged: (Gender) -> Unit,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        // ── Título ────────────────────────────────────────────────────────────
        Text(
            text  = "Cuéntanos sobre ti",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text  = "Esta información nos ayuda a personalizar tus juegos",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // ── Campo: Nombre ─────────────────────────────────────────────────────
        SectionLabel(text = "Tu nombre")
        EternaTextField(
            value             = profileForm.name,
            onValueChange     = onNameChanged,
            label             = "Nombre completo",
            isError           = profileForm.nameError != null,
            supportingText    = profileForm.nameError,
            accessibilityHint = "Campo nombre completo. ${profileForm.nameError ?: ""}"
        )

        Spacer(Modifier.height(20.dp))

        // ── Campo: Edad con Slider ─────────────────────────────────────────────
        SectionLabel(text = "Edad")
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = "60",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = "${profileForm.age} años",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = "Edad seleccionada: ${profileForm.age} años"
                }
            )
            Text(
                text  = "100",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value         = profileForm.age.toFloat(),
            onValueChange = { onAgeChanged(it.roundToInt()) },
            valueRange    = 60f..100f,
            steps         = 39,  // 41 posiciones (60–100) menos 2 extremos = 39 intermedios
            modifier      = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Deslizador de edad, valor actual ${profileForm.age} años" },
            colors        = SliderDefaults.colors(
                thumbColor       = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(Modifier.height(20.dp))

        // ── Campo: Escolaridad (Dropdown) ─────────────────────────────────────
        SectionLabel(text = "Nivel de escolaridad")
        EducationDropdown(
            selected  = profileForm.educationLevel,
            onSelect  = onEducationChanged
        )

        Spacer(Modifier.height(20.dp))

        // ── Campo: Género (RadioGroup) ────────────────────────────────────────
        SectionLabel(text = "Género")
        Column(modifier = Modifier.selectableGroup()) {
            Gender.entries.forEach { gender ->
                GenderRadioOption(
                    gender    = gender,
                    selected  = profileForm.gender == gender,
                    onSelect  = { onGenderChanged(gender) }
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Botón Continuar ───────────────────────────────────────────────────
        EternaFullWidthButton(
            text               = "Continuar",
            onClick            = onNext,
            enabled            = profileForm.isValid,
            contentDescription = if (profileForm.isValid)
                "Continuar al siguiente paso"
            else
                "Completa tu nombre para continuar"
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ── Subcomposables privados ───────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.titleMedium,
        color    = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducationDropdown(
    selected: EducationLevel,
    onSelect: (EducationLevel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded          = expanded,
        onExpandedChange  = { expanded = it },
        modifier          = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value            = selected.displayName,
            onValueChange    = {},
            readOnly         = true,
            label            = { Text("Nivel de escolaridad") },
            trailingIcon     = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle        = MaterialTheme.typography.bodyLarge,
            modifier         = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .semantics { contentDescription = "Escolaridad seleccionada: ${selected.displayName}. Pulsa para cambiar." }
        )
        ExposedDropdownMenu(
            expanded          = expanded,
            onDismissRequest  = { expanded = false }
        ) {
            EducationLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text    = { Text(level.displayName, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelect(level)
                        expanded = false
                    },
                    modifier = Modifier.semantics { contentDescription = level.displayName }
                )
            }
        }
    }
}

@Composable
private fun GenderRadioOption(
    gender: Gender,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .selectable(
                selected = selected,
                onClick  = onSelect,
                role     = Role.RadioButton
            )
            .semantics {
                contentDescription = "Género ${gender.displayName}${if (selected) ", seleccionado" else ""}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick  = null  // El selectable del Row maneja el click
        )
        Text(
            text     = "  ${gender.displayName}",
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onBackground
        )
    }
}
