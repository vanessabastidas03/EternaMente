package com.eternamente.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════════════════════
// FUENTE: Nunito — instalación en dos pasos
//
// Opción A — Google Fonts (requiere internet, recomendado para desarrollo):
//   1. Agregar dependencia: implementation(libs.compose.ui.text.google.fonts)
//   2. Agregar a libs.versions.toml:
//        compose-ui-text-google-fonts = { group = "androidx.compose.ui", name = "ui-text-google-fonts" }
//   3. Crear res/values/font_certs.xml (ver Android docs "Downloadable Fonts")
//   4. Reemplazar NunitoFamily con:
//        private val provider = GoogleFont.Provider(
//            providerAuthority = "com.google.android.gms.fonts",
//            providerPackage   = "com.google.android.gms",
//            certificates      = R.array.com_google_android_gms_fonts_certs
//        )
//        private val nunito = GoogleFont("Nunito")
//        val NunitoFamily = FontFamily(
//            Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.Normal),
//            Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.Medium),
//            Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.SemiBold),
//            Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.Bold)
//        )
//
// Opción B — Archivos bundled (fuera de línea, recomendado para producción):
//   1. Descargar nunito_regular.ttf / nunito_medium.ttf / nunito_semibold.ttf /
//      nunito_bold.ttf desde fonts.google.com/specimen/Nunito
//   2. Colocarlos en app/src/main/res/font/
//   3. Reemplazar NunitoFamily con:
//        val NunitoFamily = FontFamily(
//            Font(R.font.nunito_regular,  FontWeight.Normal),
//            Font(R.font.nunito_medium,   FontWeight.Medium),
//            Font(R.font.nunito_semibold, FontWeight.SemiBold),
//            Font(R.font.nunito_bold,     FontWeight.Bold)
//        )
// ══════════════════════════════════════════════════════════════════════════════

// Placeholder: SansSerif tiene proporciones muy similares a Nunito.
// Sustituir por NunitoFamily siguiendo una de las opciones de arriba.
private val NunitoFamily: FontFamily = FontFamily.SansSerif

/**
 * Escala tipográfica de EternaMente.
 *
 * **Criterios de diseño para adultos mayores:**
 * - Tamaño mínimo: 14 sp en cualquier texto visible de la app.
 * - `bodyLarge` (18 sp) se usa como texto de párrafo principal, en lugar del
 *   16 sp estándar de Material 3, para reducir la fatiga visual.
 * - `bodyMedium` (16 sp) equivale al `bodyLarge` estándar de M3.
 * - Pesos semibold/bold en títulos aumentan el contraste percibido sin
 *   necesitar aumentar más el tamaño.
 * - `lineHeight` generoso (1.3–1.5× el fontSize) facilita el seguimiento
 *   de línea en textos largos.
 */
val EternaTypography = Typography(

    // ── Display ─────────────────────────────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 57.sp,
        lineHeight   = 72.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 45.sp,
        lineHeight   = 56.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 36.sp,
        lineHeight   = 44.sp,
        letterSpacing = 0.sp
    ),

    // ── Headline ─────────────────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 32.sp,
        lineHeight   = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 28.sp,
        lineHeight   = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 24.sp,
        lineHeight   = 32.sp,
        letterSpacing = 0.sp
    ),

    // ── Title ────────────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 18.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 16.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Body — mínimo 16 sp según guía de accesibilidad ──────────────────────
    bodyLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 18.sp,   // Aumentado respecto al estándar M3 (16 sp)
        lineHeight   = 28.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,   // Mínimo absoluto en la app
        lineHeight   = 20.sp,
        letterSpacing = 0.4.sp
    ),

    // ── Label ────────────────────────────────────────────────────────────────
    labelLarge = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,   // Respetamos mínimo — no bajamos a 12 sp
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily   = NunitoFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,   // Mínimo absoluto — nunca menos de 14 sp
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp
    )
)
