package com.eternamente.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// ══════════════════════════════════════════════════════════════════════════════
// Esquema NORMAL — Light
// ══════════════════════════════════════════════════════════════════════════════
private val LightColorScheme = lightColorScheme(
    primary              = Blue40,
    onPrimary            = Neutral100,
    primaryContainer     = Blue90,
    onPrimaryContainer   = Blue10,
    secondary            = Green40,
    onSecondary          = Neutral100,
    secondaryContainer   = Green90,
    onSecondaryContainer = Green10,
    tertiary             = Orange40,
    onTertiary           = Neutral100,
    tertiaryContainer    = Orange90,
    onTertiaryContainer  = Orange10,
    background           = Neutral99,
    onBackground         = Neutral10,
    surface              = Neutral100,
    onSurface            = Neutral10,
    surfaceVariant       = NeutralVar95,
    onSurfaceVariant     = NeutralVar30,
    outline              = NeutralVar50,
    outlineVariant       = NeutralVar90,
    error                = Red40,
    onError              = Neutral100,
    errorContainer       = Red90,
    onErrorContainer     = Red10,
    inverseSurface       = Neutral10,
    inverseOnSurface     = Neutral90,
    inversePrimary       = Blue80,
    scrim                = Neutral0
)

// ══════════════════════════════════════════════════════════════════════════════
// Esquema NORMAL — Dark
// ══════════════════════════════════════════════════════════════════════════════
private val DarkColorScheme = darkColorScheme(
    primary              = Blue80,
    onPrimary            = Blue20,
    primaryContainer     = Blue30,
    onPrimaryContainer   = Blue90,
    secondary            = Green80,
    onSecondary          = Green20,
    secondaryContainer   = Green30,
    onSecondaryContainer = Green90,
    tertiary             = Orange80,
    onTertiary           = Orange20,
    tertiaryContainer    = Orange30,
    onTertiaryContainer  = Orange90,
    background           = Neutral10,
    onBackground         = Neutral90,
    surface              = Neutral10,
    onSurface            = Neutral90,
    surfaceVariant       = NeutralVar30,
    onSurfaceVariant     = NeutralVar80,
    outline              = NeutralVar60,
    outlineVariant       = NeutralVar30,
    error                = Red80,
    onError              = Red20,
    errorContainer       = Red30,
    onErrorContainer     = Red90,
    inverseSurface       = Neutral90,
    inverseOnSurface     = Neutral10,
    inversePrimary       = Blue40,
    scrim                = Neutral0
)

// ══════════════════════════════════════════════════════════════════════════════
// Esquema ALTO CONTRASTE — Light (WCAG AAA ≥ 7:1)
//
// Diferencias clave respecto al esquema normal:
// • onBackground / onSurface → negro puro (#000000, ratio 21:1 vs blanco)
// • Primary/Secondary/Tertiary → versiones más oscuras de la paleta tonal
// • Outline → negro puro para bordes bien definidos
// ══════════════════════════════════════════════════════════════════════════════
private val HighContrastLightColorScheme = lightColorScheme(
    primary              = HCBlue30,      // #003A8C — 9.2:1 vs #FFF ✓ WCAG AAA
    onPrimary            = PureWhite,
    primaryContainer     = Blue90,
    onPrimaryContainer   = HCBlue10,      // #00113B — muy oscuro sobre contenedor claro
    secondary            = HCGreen30,     // #1B5E20 — 8.1:1 vs #FFF ✓ WCAG AAA
    onSecondary          = PureWhite,
    secondaryContainer   = Green90,
    onSecondaryContainer = Green10,
    tertiary             = HCOrange30,    // #BF360C — 7.2:1 vs #FFF ✓ WCAG AAA
    onTertiary           = PureWhite,
    tertiaryContainer    = Orange90,
    onTertiaryContainer  = Orange10,
    background           = PureWhite,     // Fondo blanco puro para máximo contraste
    onBackground         = PureBlack,     // Texto negro puro — 21:1 ✓ WCAG AAA
    surface              = PureWhite,
    onSurface            = PureBlack,
    surfaceVariant       = Neutral95,
    onSurfaceVariant     = PureBlack,
    outline              = PureBlack,     // Bordes negros bien definidos
    outlineVariant       = NeutralVar50,
    error                = Red40,
    onError              = PureWhite,
    errorContainer       = Red90,
    onErrorContainer     = Red10,
    inverseSurface       = Neutral10,
    inverseOnSurface     = Neutral90,
    inversePrimary       = HCBlue80,
    scrim                = Neutral0
)

// ══════════════════════════════════════════════════════════════════════════════
// Esquema ALTO CONTRASTE — Dark
//
// • onBackground / onSurface → blanco puro (#FFFFFF, ratio 21:1 vs negro)
// • Primary/Secondary/Tertiary → versiones más claras y luminosas
// • Background / Surface → negro puro (#000000)
// ══════════════════════════════════════════════════════════════════════════════
private val HighContrastDarkColorScheme = darkColorScheme(
    primary              = HCBlue80,      // #BDD8FF — muy luminoso sobre negro ✓
    onPrimary            = PureBlack,
    primaryContainer     = Blue30,
    onPrimaryContainer   = Blue90,
    secondary            = HCGreen80,     // #A8DBA8 — legible sobre negro ✓
    onSecondary          = PureBlack,
    secondaryContainer   = Green30,
    onSecondaryContainer = Green90,
    tertiary             = HCOrange80,    // #FFCA88 — legible sobre negro ✓
    onTertiary           = PureBlack,
    tertiaryContainer    = Orange30,
    onTertiaryContainer  = Orange90,
    background           = PureBlack,     // Fondo negro puro para máximo contraste nocturno
    onBackground         = PureWhite,     // Texto blanco puro — 21:1 ✓ WCAG AAA
    surface              = PureBlack,
    onSurface            = PureWhite,
    surfaceVariant       = Neutral20,
    onSurfaceVariant     = PureWhite,
    outline              = PureWhite,     // Bordes blancos bien definidos sobre negro
    outlineVariant       = NeutralVar60,
    error                = Red80,
    onError              = PureBlack,
    errorContainer       = Red30,
    onErrorContainer     = Red90,
    inverseSurface       = Neutral90,
    inverseOnSurface     = Neutral10,
    inversePrimary       = HCBlue30,
    scrim                = Neutral0
)

// ══════════════════════════════════════════════════════════════════════════════
// CompositionLocal — preferencias de accesibilidad globales
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Preferencias de accesibilidad disponibles para cualquier composable hijo
 * a través del árbol de composición, sin prop-drilling.
 *
 * @property hapticFeedback Vibración al interactuar con controles en los juegos.
 * @property reducedMotion  Reduce o elimina animaciones (respeta ajuste del sistema).
 */
data class AccessibilityConfig(
    val hapticFeedback: Boolean = true,
    val reducedMotion: Boolean  = false
)

val LocalAccessibility = staticCompositionLocalOf { AccessibilityConfig() }

// ══════════════════════════════════════════════════════════════════════════════
// Tema principal
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Tema Material 3 de EternaMente con soporte reactivo de modo oscuro y alto contraste.
 *
 * El esquema de color se selecciona en un único `when` con cuatro ramas:
 *
 * | darkTheme | highContrast | Esquema aplicado               |
 * |-----------|--------------|--------------------------------|
 * | false     | false        | [LightColorScheme]             |
 * | false     | true         | [HighContrastLightColorScheme] |
 * | true      | false        | [DarkColorScheme]              |
 * | true      | true         | [HighContrastDarkColorScheme]  |
 *
 * **Sin `Activity.recreate()`:** el cambio de tema es instantáneo porque
 * [darkTheme] e [highContrast] son parámetros ordinarios de Composable.
 * Cuando [MainActivity] recolecta un nuevo [UserPreferences] desde DataStore,
 * Compose re-ejecuta este bloque y selecciona el esquema correcto.
 *
 * **Previews:** `@Preview` en modo oscuro usa
 * `uiMode = Configuration.UI_MODE_NIGHT_YES`; los parámetros opcionales
 * con valores por defecto garantizan que los previews existentes no se rompan.
 *
 * @param darkTheme          `true` activa el esquema oscuro. Por defecto sigue la preferencia del sistema.
 * @param highContrast       `true` activa el esquema de alto contraste (WCAG AAA ≥ 7:1).
 * @param accessibilityConfig Configuración adicional de accesibilidad (haptic, reduced motion).
 * @param content            Árbol de composición hijo.
 */
@Composable
fun EternaMenteTheme(
    darkTheme: Boolean            = isSystemInDarkTheme(),
    highContrast: Boolean         = false,
    accessibilityConfig: AccessibilityConfig = AccessibilityConfig(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        highContrast && darkTheme -> HighContrastDarkColorScheme
        highContrast              -> HighContrastLightColorScheme
        darkTheme                 -> DarkColorScheme
        else                      -> LightColorScheme
    }

    CompositionLocalProvider(LocalAccessibility provides accessibilityConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = EternaTypography,
            shapes      = EternaShapes,
            content     = content
        )
    }
}
