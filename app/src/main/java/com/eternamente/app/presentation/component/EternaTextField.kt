package com.eternamente.app.presentation.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.ui.theme.EternaMenteTheme

// ══════════════════════════════════════════════════════════════════════════════
// EternaTextField — Campo de texto accesible para adultos mayores
//
// El label SIEMPRE es visible (flotante o a tamaño completo), nunca oculto,
// lo cual reduce la carga cognitiva al recordar qué campo se está rellenando.
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Campo de texto con borde (Material 3 Outlined TextField) optimizado para
 * adultos mayores.
 *
 * **Accesibilidad garantizada:**
 * - El [label] nunca desaparece: flota arriba del borde cuando el campo tiene foco.
 * - [supportingText] muestra errores o ayuda contextual debajo del campo.
 * - La semántica incluye el estado de error para que TalkBack lo anuncie.
 * - El tamaño mínimo táctil se hereda del componente M3 (≥ 48 dp de altura).
 *
 * @param value          Valor actual del campo.
 * @param onValueChange  Lambda invocada con cada cambio de texto.
 * @param label          Etiqueta descriptiva, siempre visible.
 * @param modifier       Modificador externo; por defecto ocupa el ancho completo.
 * @param isError        `true` → borde rojo + [supportingText] en color error.
 * @param supportingText Texto de ayuda o error debajo del campo; `null` para omitir.
 * @param keyboardType   Tipo de teclado (texto, email, número, etc.).
 * @param imeAction      Acción del botón de acción del teclado.
 * @param onImeAction    Lambda del botón de acción del teclado.
 * @param trailingIcon   Icono opcional a la derecha del campo.
 * @param enabled        `false` → campo deshabilitado.
 * @param readOnly       `true` → campo de solo lectura.
 * @param maxLines       Número máximo de líneas visibles (por defecto 1 — campo de una línea).
 * @param accessibilityHint Descripción adicional para TalkBack.
 */
@Composable
fun EternaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    maxLines: Int = 1,
    accessibilityHint: String = label
) {
    OutlinedTextField(
        value            = value,
        onValueChange    = onValueChange,
        label            = { Text(label) },
        modifier         = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityHint + if (isError) ", error" else "" },
        isError          = isError,
        supportingText   = supportingText?.let {
            {
                Text(
                    text  = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        keyboardOptions  = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = imeAction
        ),
        keyboardActions  = KeyboardActions(onAny = { onImeAction() }),
        trailingIcon     = trailingIcon,
        enabled          = enabled,
        readOnly         = readOnly,
        maxLines         = maxLines,
        textStyle        = MaterialTheme.typography.bodyLarge,
        singleLine       = maxLines == 1
    )
}

// ── Variante de contraseña ────────────────────────────────────────────────────

/**
 * Campo de texto especializado para contraseñas con icono de mostrar/ocultar.
 *
 * El icono de visibilidad es accesible: su `contentDescription` cambia dinámicamente
 * entre "Mostrar contraseña" y "Ocultar contraseña" para TalkBack.
 */
@Composable
fun EternaPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {}
) {
    var passwordVisible by remember { mutableStateOf(false) }

    EternaTextField(
        value             = value,
        onValueChange     = onValueChange,
        label             = label,
        modifier          = modifier,
        isError           = isError,
        supportingText    = supportingText,
        keyboardType      = KeyboardType.Password,
        imeAction         = imeAction,
        onImeAction       = onImeAction,
        accessibilityHint = "$label, campo de contraseña",
        trailingIcon      = {
            val desc = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
            IconButton(
                onClick  = { passwordVisible = !passwordVisible },
                modifier = Modifier.semantics { contentDescription = desc }
            ) {
                Icon(
                    imageVector        = if (passwordVisible) Icons.Filled.VisibilityOff
                    else Icons.Filled.Visibility,
                    contentDescription = null,  // En el IconButton ya está la semántica
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "EternaTextField — Claro", showBackground = true)
@Preview(name = "EternaTextField — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaTextFieldPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            EternaTextField(
                value         = "usuario@email.com",
                onValueChange = {},
                label         = "Correo electrónico",
                keyboardType  = KeyboardType.Email,
                trailingIcon  = {
                    Icon(
                        imageVector        = Icons.Filled.Email,
                        contentDescription = "Correo electrónico"
                    )
                }
            )
        }
    }
}

@Preview(name = "EternaTextField con error — Claro", showBackground = true)
@Preview(name = "EternaTextField con error — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaTextFieldErrorPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            EternaTextField(
                value         = "usuario",
                onValueChange = {},
                label         = "Correo electrónico",
                isError       = true,
                supportingText = "Introduce un correo válido"
            )
        }
    }
}

@Preview(name = "EternaPasswordField — Claro", showBackground = true)
@Preview(name = "EternaPasswordField — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaPasswordFieldPreview() {
    EternaMenteTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            EternaPasswordField(
                value         = "contraseña123",
                onValueChange = {},
                label         = "Contraseña"
            )
        }
    }
}
