package com.eternamente.app.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val PIN_LENGTH = 6

/**
 * Campo de entrada de PIN de 6 dígitos con indicadores visuales circulares.
 *
 * Muestra 6 círculos: rellenos (●) para los dígitos ingresados y vacíos (○)
 * para los pendientes. El teclado numérico se activa automáticamente.
 *
 * **Accesibilidad:**
 * - El [contentDescription] anuncia cuántos dígitos se han ingresado.
 * - El campo subyacente es invisible; los círculos son decorativos.
 * - Compatible con TalkBack y switch access.
 *
 * **Seguridad:**
 * - Usa [PasswordVisualTransformation] en el [BasicTextField] subyacente.
 * - El PIN nunca se renderiza como texto visible.
 *
 * @param pin          Valor actual del PIN (solo dígitos, longitud ≤ 6).
 * @param onPinChanged Callback invocado con cada cambio de dígito.
 * @param modifier     Modificador externo.
 * @param isError      `true` → círculos en color de error.
 * @param dotSize      Tamaño de cada círculo (default 16 dp).
 * @param contentDescription Descripción para TalkBack.
 */
@Composable
fun PinInputField(
    pin: String,
    onPinChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    dotSize: Dp = 16.dp,
    contentDescription: String = "Campo de PIN de $PIN_LENGTH dígitos, ${pin.length} ingresados"
) {
    val focusRequester = remember { FocusRequester() }
    val activeColor    = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val emptyColor     = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        // TextField invisible que captura la entrada del teclado
        BasicTextField(
            value           = pin,
            onValueChange   = { new ->
                if (new.length <= PIN_LENGTH && new.all { it.isDigit() }) {
                    onPinChanged(new)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier        = Modifier
                .focusRequester(focusRequester)
                .size(1.dp)  // Invisible pero enfocable
        )

        // Indicadores visuales de cada posición del PIN
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            repeat(PIN_LENGTH) { index ->
                val isFilled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(dotSize + 8.dp)
                        .border(
                            width = 2.dp,
                            color = if (isFilled) activeColor else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            color = if (isFilled) activeColor.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFilled) {
                        Box(
                            modifier = Modifier
                                .size(dotSize * 0.55f)
                                .background(activeColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
