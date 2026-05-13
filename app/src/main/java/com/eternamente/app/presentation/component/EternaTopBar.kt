package com.eternamente.app.presentation.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.ui.theme.EternaMenteTheme

// ══════════════════════════════════════════════════════════════════════════════
// EternaTopBar — Top App Bar con título centrado (Material 3)
//
// El título centrado (vs alineado a la izquierda) mejora la simetría visual
// y facilita la lectura para usuarios con visión periférica reducida.
// ══════════════════════════════════════════════════════════════════════════════

private val NavigationIconSize = 48.dp  // Mínimo táctil garantizado

/**
 * Barra de aplicación superior con título centrado.
 *
 * Usa [CenterAlignedTopAppBar] de Material 3. El botón de retroceso se muestra
 * automáticamente cuando [onNavigateBack] no es `null`.
 *
 * **Accesibilidad:**
 * - El botón de retroceso tiene `contentDescription = "Volver atrás"`.
 * - El ícono es auto-espejado en locales RTL (LTR→RTL friendly con AutoMirrored).
 * - La barra respeta las insets del sistema (configuradas en el Scaffold padre).
 *
 * @param title           Texto del título. Se trunca con `…` si es demasiado largo.
 * @param modifier        Modificador externo.
 * @param onNavigateBack  Si no es `null`, muestra el botón de retroceso con esta lambda.
 * @param actions         Bloque para íconos de acción en el extremo derecho.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EternaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text     = title,
                style    = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(
                    onClick  = onNavigateBack,
                    modifier = Modifier
                        .size(NavigationIconSize)
                        .semantics { contentDescription = "Volver atrás" }
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,  // Semántica en el IconButton
                        tint               = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor        = MaterialTheme.colorScheme.surface,
            titleContentColor     = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor     = MaterialTheme.colorScheme.onSurface
        )
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Previews
// ══════════════════════════════════════════════════════════════════════════════

@Preview(name = "EternaTopBar sin retroceso — Claro", showBackground = true)
@Preview(name = "EternaTopBar sin retroceso — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaTopBarNoBackPreview() {
    EternaMenteTheme {
        Surface { EternaTopBar(title = "EternaMente") }
    }
}

@Preview(name = "EternaTopBar con retroceso — Claro", showBackground = true)
@Preview(name = "EternaTopBar con retroceso — Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EternaTopBarWithBackPreview() {
    EternaMenteTheme {
        Surface {
            EternaTopBar(
                title          = "Instrucciones del juego",
                onNavigateBack = {}
            )
        }
    }
}

@Preview(name = "EternaTopBar con acciones — Claro", showBackground = true)
@Composable
private fun EternaTopBarWithActionsPreview() {
    EternaMenteTheme {
        Surface {
            EternaTopBar(
                title          = "Panel principal",
                onNavigateBack = {},
                actions        = {
                    IconButton(
                        onClick  = {},
                        modifier = Modifier.semantics { contentDescription = "Más opciones" }
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.MoreVert,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    }
}
