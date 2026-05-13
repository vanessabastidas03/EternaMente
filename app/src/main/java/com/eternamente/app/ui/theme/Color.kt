package com.eternamente.app.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════════
// EternaMente — Paleta de colores tonal (Material Design 3)
//
// Nomenclatura: <Hue><Tono> donde el tono sigue la escala M3 (0=negro, 100=blanco).
// Los colores marcados con * son los que se asignan directamente al ColorScheme.
// ══════════════════════════════════════════════════════════════════════════════

// ── Primary: Azul confiable (#1565C0) ────────────────────────────────────────
// Evoca seguridad, salud y tecnología. Elegido por alta legibilidad en fondos
// claros y oscuros sin causar fatiga visual en adultos mayores.
val Blue10  = Color(0xFF001D36)   // onPrimaryContainer (light)
val Blue20  = Color(0xFF003258)   // onPrimary (dark)
val Blue30  = Color(0xFF004880)   // primaryContainer (dark)
val Blue40  = Color(0xFF1565C0)   // * primary (light)
val Blue80  = Color(0xFF9ECAFF)   // * primary (dark)
val Blue90  = Color(0xFFD1E4FF)   // * primaryContainer (light), onPrimaryContainer (dark)

// ── Secondary: Verde logro (#2E7D32) ─────────────────────────────────────────
// Refuerzo positivo en gamificación. Verde oscuro con suficiente contraste
// contra fondos blancos (ratio 5.4:1 con #FFFFFF según WCAG 2.1 AA).
val Green10 = Color(0xFF002204)   // onSecondaryContainer (light)
val Green20 = Color(0xFF003909)   // onSecondary (dark)
val Green30 = Color(0xFF005712)   // secondaryContainer (dark)
val Green40 = Color(0xFF2E7D32)   // * secondary (light)
val Green80 = Color(0xFF85C983)   // * secondary (dark)
val Green90 = Color(0xFFB5EDB4)   // * secondaryContainer (light), onSecondaryContainer (dark)

// ── Tertiary: Naranja alerta suave (#F57F17) ──────────────────────────────────
// Alerta no alarmante. Evita el rojo de error para señales de "atención"
// sin generar ansiedad clínica en el usuario mayor.
val Orange10 = Color(0xFF210C00)  // onTertiaryContainer (light)
val Orange20 = Color(0xFF3E1B00)  // onTertiary (dark)
val Orange30 = Color(0xFF7F4000)  // tertiaryContainer (dark)
val Orange40 = Color(0xFFF57F17)  // * tertiary (light)
val Orange80 = Color(0xFFFFB74D)  // * tertiary (dark)
val Orange90 = Color(0xFFFFE0B2)  // * tertiaryContainer (light), onTertiaryContainer (dark)

// ── Error: Rojo clínico (#C62828) ────────────────────────────────────────────
// Reservado para estados de error real (validación, fallos de red).
// No se usa para alertas cognitivas (eso es orange/tertiary).
val Red10 = Color(0xFF410001)     // onErrorContainer (light)
val Red20 = Color(0xFF690001)     // onError (dark)
val Red30 = Color(0xFF93000A)     // errorContainer (dark)
val Red40 = Color(0xFFC62828)     // * error (light)
val Red80 = Color(0xFFFF8A8A)     // * error (dark)
val Red90 = Color(0xFFFFEDEA)     // * errorContainer (light), onErrorContainer (dark)

// ── Neutral ────────────────────────────────────────────────────────────────────
val Neutral0   = Color(0xFF000000)
val Neutral10  = Color(0xFF1A1C1E)  // * onBackground, onSurface (light)
val Neutral90  = Color(0xFFE2E2E6)  // * onBackground, onSurface (dark)
val Neutral95  = Color(0xFFF0F0F4)
val Neutral99  = Color(0xFFFAFAFA)  // * background (light)
val Neutral100 = Color(0xFFFFFFFF)  // * surface (light), onPrimary/Secondary/Tertiary/Error (light)

// ── Neutral Variant ────────────────────────────────────────────────────────────
val NeutralVar30 = Color(0xFF44474E)  // * onSurfaceVariant (light)
val NeutralVar50 = Color(0xFF74777F)  // * outline (light)
val NeutralVar60 = Color(0xFF8E9099)  // * outline (dark)
val NeutralVar80 = Color(0xFFC4C7CF)  // * onSurfaceVariant (dark)
val NeutralVar90 = Color(0xFFE1E2EC)  // * outlineVariant (light)
val NeutralVar95 = Color(0xFFF2F4F9)  // * surfaceVariant (light)
