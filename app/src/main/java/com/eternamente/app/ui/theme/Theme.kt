package com.eternamente.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// ══════════════════════════════════════════════════════════════════════════════
// Esquemas de color — Light
// ══════════════════════════════════════════════════════════════════════════════
private val LightColorScheme = lightColorScheme(
    // Primary — Azul confiable
    primary              = Blue40,
    onPrimary            = Neutral100,
    primaryContainer     = Blue90,
    onPrimaryContainer   = Blue10,
    // Secondary — Verde logro
    secondary            = Green40,
    onSecondary          = Neutral100,
    secondaryContainer   = Green90,
    onSecondaryContainer = Green10,
    // Tertiary — Naranja alerta suave
    tertiary             = Orange40,
    onTertiary           = Neutral100,
    tertiaryContainer    = Orange90,
    onTertiaryContainer  = Orange10,
    // Background & Surface
    background           = Neutral99,
    onBackground         = Neutral10,
    surface              = Neutral100,
    onSurface            = Neutral10,
    surfaceVariant       = NeutralVar95,
    onSurfaceVariant     = NeutralVar30,
    // Outline
    outline              = NeutralVar50,
    outlineVariant       = NeutralVar90,
    // Error
    error                = Red40,
    onError              = Neutral100,
    errorContainer       = Red90,
    onErrorContainer     = Red10,
    // Inverse
    inverseSurface       = Neutral10,
    inverseOnSurface     = Neutral90,
    inversePrimary       = Blue80,
    // Scrim
    scrim                = Neutral0
)

// ══════════════════════════════════════════════════════════════════════════════
// Esquemas de color — Dark
// Ajustados para contraste nocturno sin deslumbramiento (fondo #1A1C1E).
// Los colores de acción (primary, secondary, tertiary) se mapean a los
// tonos 80 de la paleta (claros sobre fondo oscuro).
// ══════════════════════════════════════════════════════════════════════════════
private val DarkColorScheme = darkColorScheme(
    // Primary
    primary              = Blue80,
    onPrimary            = Blue20,
    primaryContainer     = Blue30,
    onPrimaryContainer   = Blue90,
    // Secondary
    secondary            = Green80,
    onSecondary          = Green20,
    secondaryContainer   = Green30,
    onSecondaryContainer = Green90,
    // Tertiary
    tertiary             = Orange80,
    onTertiary           = Orange20,
    tertiaryContainer    = Orange30,
    onTertiaryContainer  = Orange90,
    // Background & Surface
    background           = Neutral10,
    onBackground         = Neutral90,
    surface              = Neutral10,
    onSurface            = Neutral90,
    surfaceVariant       = NeutralVar30,
    onSurfaceVariant     = NeutralVar80,
    // Outline
    outline              = NeutralVar60,
    outlineVariant       = NeutralVar30,
    // Error
    error                = Red80,
    onError              = Red20,
    errorContainer       = Red30,
    onErrorContainer     = Red90,
    // Inverse
    inverseSurface       = Neutral90,
    inverseOnSurface     = Neutral10,
    inversePrimary       = Blue40,
    // Scrim
    scrim                = Neutral0
)

// ══════════════════════════════════════════════════════════════════════════════
// CompositionLocal para acceder a ajustes de accesibilidad desde cualquier
// composable hijo, sin necesidad de prop-drilling.
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Configuración de accesibilidad dinámica expuesta a través del árbol de composición.
 *
 * @property highContrast  Activa una paleta de mayor contraste (fondo #000 / texto #FFF).
 * @property reducedMotion Desactiva animaciones cuando el usuario lo solicita.
 * @property largeText     Aumenta la escala tipográfica en 1.25× en toda la app.
 */
data class AccessibilityConfig(
    val highContrast: Boolean  = false,
    val reducedMotion: Boolean = false,
    val largeText: Boolean     = false
)

val LocalAccessibility = staticCompositionLocalOf { AccessibilityConfig() }

// ══════════════════════════════════════════════════════════════════════════════
// Tema principal
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Tema Material 3 de EternaMente.
 *
 * **El color dinámico (Dynamic Color de Android 12+) está desactivado.**
 * EternaMente usa una paleta fija elegida para garantizar:
 * - Contraste mínimo WCAG 2.1 AA en todos los pares de color.
 * - Coherencia de marca en todos los dispositivos.
 * - Legibilidad óptima para usuarios mayores de 60 años.
 *
 * @param darkTheme          Forzar modo oscuro; por defecto sigue la preferencia del sistema.
 * @param accessibilityConfig Ajustes de accesibilidad para alto contraste, texto grande, etc.
 * @param content            Árbol de composición hijo.
 */
@Composable
fun EternaMenteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accessibilityConfig: AccessibilityConfig = AccessibilityConfig(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAccessibility provides accessibilityConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = EternaTypography,
            shapes      = EternaShapes,
            content     = content
        )
    }
}
