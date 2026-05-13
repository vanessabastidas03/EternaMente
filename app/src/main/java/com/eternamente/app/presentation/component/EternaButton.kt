package com.eternamente.app.presentation.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.ui.theme.EternaMenteTheme

// ══════════════════════════════════════════════════════════════════════════════
// EternaButton — Variantes de botón para EternaMente
//
// Diseñadas para adultos mayores:
// - Altura mínima 56 dp (supera el mínimo táctil de 48 dp).
// - Texto en bodyLarge (18 sp) para legibilidad.
// - [contentDescription] requerido para TalkBack.
// ══════════════════════════════════════════════════════════════════════════════

private val ButtonMinHeight = 56.dp
private val ButtonHorizontalPadding = 24.dp
private val ButtonVerticalPadding = 14.dp
private val IconButtonSize = 24.dp
private val IconSpacing = 8.dp

// ── Primary (Filled) ──────────────────────────────────────────────────────────

/**
 * Botón principal de acción relleno (Material 3 Filled Button).
 *
 * Uso: acción principal de cada pantalla (ej. "Comenzar sesión", "Guardar").
 * Solo debe haber un [EternaButton] de nivel primario por pantalla.
 *
 * @param text               Etiqueta visible del botón.
 * @param onClick            Lambda invocada al pulsar.
 * @param modifier           Modificador opcional.
 * @param enabled            `false` → estado deshabilitado con feedback visual.
 * @param contentDescription Descripción para TalkBack; por defecto igual a [text].
 * @param leadingIcon        Icono opcional a la izquierda del texto.
 * @param isLoading          Sustituye el contenido por un spinner mientras es `true`.
 */
@Composable
fun EternaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text,
    leadingIcon: ImageVector? = null,
    isLoading: Boolean = false
) {
    Button(
        onClick          = onClick,
        modifier         = modifier
            .heightIn(min = ButtonMinHeight)
            .semantics { this.contentDescription = contentDescription },
        enabled          = enabled && !isLoading,
        contentPadding   = PaddingValues(
            horizontal = ButtonHorizontalPadding,
            vertical   = ButtonVerticalPadding
        )
    ) {
        ButtonContent(
            text        = text,
            leadingIcon = leadingIcon,
            isLoading   = isLoading,
            tint        = MaterialTheme.colorScheme.onPrimary
        )
    }
}

// ── Secondary (Outlined) ───────────────────────────────────────────────────────

/**
 * Botón de acción secundaria con borde (Material 3 Outlined Button).
 *
 * Uso: acción alternativa cuando ya existe un [EternaButton] primario
 * (ej. "Cancelar", "Ver más tarde").
 */
@Composable
fun EternaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text,
    leadingIcon: ImageVector? = null
) {
    OutlinedButton(
        onClick        = onClick,
        modifier       = modifier
            .heightIn(min = ButtonMinHeight)
            .semantics { this.contentDescription = contentDescription },
        enabled        = enabled,
        contentPadding = PaddingValues(
            horizontal = ButtonHorizontalPadding,
            vertical   = ButtonVerticalPadding
        )
    ) {
        ButtonContent(
            text        = text,
            leadingIcon = leadingIcon,
            isLoading   = false,
            tint        = MaterialTheme.colorScheme.primary
        )
    }
}

// ── Text Button ───────────────────────────────────────────────────────────────

/**
 * Botón de texto sin fondo ni borde (Material 3 Text Button).
 *
 * Uso: acciones de menor importancia, como "Saltar" o "¿Olvidaste tu contraseña?".
 * Tiene prioridad visual mínima; úsalo con moderación.
 */
@Composable
fun EternaTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    TextButton(
        onClick  = onClick,
        modifier = modifier
            .heightIn(min = ButtonMinHeight)
            .semantics { this.contentDescription = contentDescription },
        enabled  = enabled
    ) {
        Text(
            text      = text,
            style     = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

// ── Full-width variant ─────────────────────────────────────────────────────────

/**
 * Versión de ancho completo del botón principal.
 *
 * Conveniente para acciones únicas en pantalla que deben llamar la atención
 * (ej. botón al final de un formulario).
 */
@Composable
fun EternaFullWidthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text,
    isLoading: Boolean = false
) {
    EternaButton(
        text               = text,
        onClick            = onClick,
        modifier           = modifier.fillMaxWidth(),
        enabled            = enabled,
        contentDescription = contentDescription,
        isLoading          = isLoading
    )
}

// ── Internal shared content ────────────────────────────────────────────────────

@Composable
private fun ButtonContent(
    text: String,
    leadingIcon: ImageVector?,
    isLoading: Boolean,
    tint: androidx.compose.ui.graphics.Color
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier  = Modifier.size(IconButtonSize),
            color     = tint,
            strokeWidth = 2.dp
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    imageVector        = leadingIcon,
                    contentDescription = null,  // Decorativo; el botón tiene su propia semantics
                    modifier           = Modifier.size(IconButtonSize),
                    tint               = tint
                )
                Spacer(Modifier.width(IconSpacing))
            }
            Text(
                text      = text,
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "EternaButton — Claro", showBackground = true)
@Preview(name = "EternaButton — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaButtonPreview() {
    EternaMenteTheme {
        Surface {
            EternaButton(text = "Comenzar sesión", onClick = {})
        }
    }
}

@Preview(name = "EternaButton cargando — Claro", showBackground = true)
@Composable
private fun EternaButtonLoadingPreview() {
    EternaMenteTheme {
        Surface {
            EternaButton(text = "Iniciando...", onClick = {}, isLoading = true)
        }
    }
}

@Preview(name = "EternaOutlinedButton — Claro", showBackground = true)
@Preview(name = "EternaOutlinedButton — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaOutlinedButtonPreview() {
    EternaMenteTheme {
        Surface {
            EternaOutlinedButton(text = "Ya tengo cuenta", onClick = {})
        }
    }
}

@Preview(name = "EternaTextButton — Claro", showBackground = true)
@Preview(name = "EternaTextButton — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaTextButtonPreview() {
    EternaMenteTheme {
        Surface {
            EternaTextButton(text = "¿Olvidaste tu contraseña?", onClick = {})
        }
    }
}
