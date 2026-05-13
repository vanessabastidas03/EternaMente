package com.eternamente.app.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val PIN_LENGTH = 6

/**
 * Campo de entrada de PIN de 6 dígitos con indicadores visuales de cuadros.
 *
 * ## Por qué funciona la interacción
 * Un [BasicTextField] invisible (`height = 1.dp`) captura el input del teclado.
 * El [Box] exterior tiene `Modifier.clickable` que llama a [FocusRequester.requestFocus]
 * en cada toque. Los cuadros indicadores internos no tienen `clickable`, por lo que
 * el evento de toque burbujea al padre y el Box lo captura con su `clickable`.
 *
 * ## Navegación entre campos
 * El parámetro [focusRequester] se expone para control externo:
 * ```kotlin
 * val confirmFr = remember { FocusRequester() }
 * PinInputField(imeAction = ImeAction.Next, onImeAction = { confirmFr.requestFocus() })
 * PinInputField(focusRequester = confirmFr, imeAction = ImeAction.Done)
 * ```
 *
 * @param pin              Valor actual (solo dígitos, longitud ≤ 6).
 * @param onPinChanged     Callback por cada cambio de dígito.
 * @param modifier         Modificador externo.
 * @param isError          `true` → cuadros en color de error.
 * @param focusRequester   Control de foco externo; por defecto se crea internamente.
 * @param imeAction        Acción del botón de teclado (Next / Done).
 * @param onImeAction      Lambda al pulsar el botón de acción del teclado.
 * @param dotSize          Tamaño del punto relleno en cada cuadro.
 * @param contentDescription Descripción para TalkBack.
 */
@Composable
fun PinInputField(
    pin: String,
    onPinChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    dotSize: Dp = 16.dp,
    contentDescription: String = "Campo de PIN de $PIN_LENGTH dígitos, ${pin.length} ingresados"
) {
    val activeColor = if (isError) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            // ← CORRECCIÓN PRINCIPAL: cualquier toque en el Box (incluyendo sobre los
            // cuadros visuales que no consumen el evento) llega aquí y solicita el foco.
            .clickable { focusRequester.requestFocus() }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {

        // BasicTextField invisible — todo el ancho, 1 dp de alto.
        // Invisible visualmente pero recibe input del teclado tras requestFocus().
        BasicTextField(
            value            = pin,
            onValueChange    = { new ->
                if (new.length <= PIN_LENGTH && new.all { it.isDigit() }) {
                    onPinChanged(new)
                }
            },
            keyboardOptions  = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction    = imeAction
            ),
            keyboardActions  = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() },
                onGo   = { onImeAction() }
            ),
            visualTransformation = PasswordVisualTransformation(),
            modifier         = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth()
                .height(1.dp)   // El clickable del Box gestiona el acceso táctil
        )

        // ── Cuadros indicadores visuales ─────────────────────────────────────────
        // No tienen Modifier.clickable → el toque burbujea al Box padre.
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
