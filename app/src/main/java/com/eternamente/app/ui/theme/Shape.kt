package com.eternamente.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Esquema de formas de EternaMente.
 *
 * Se define una escala de esquinas redondeadas que:
 * - Proyecta calidez y accesibilidad (sin aristas agudas).
 * - Mantiene coherencia visual en tarjetas, botones y diálogos.
 * - Los componentes [EternaButton] y [EternaCard] usan `medium` (12 dp) como base.
 *
 * El tema M3 mapea estas formas a los roles semánticos correspondientes:
 * `extraSmall` → chips, tooltips | `small` → campos de texto |
 * `medium` → cards, FAB pequeño | `large` → bottom sheets, FAB grande |
 * `extraLarge` → diálogos, banners
 */
val EternaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
