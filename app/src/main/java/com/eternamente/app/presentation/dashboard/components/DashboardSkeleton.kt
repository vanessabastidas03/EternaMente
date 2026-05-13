package com.eternamente.app.presentation.dashboard.components

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eternamente.app.ui.theme.EternaMenteTheme

/**
 * Skeleton de carga del Dashboard.
 *
 * Muestra siluetas animadas con un efecto shimmer mientras los datos
 * se cargan desde Room. Imita la forma y posición de los widgets reales
 * para que la transición sea suave cuando los datos lleguen.
 *
 * El contenedor raíz tiene `contentDescription = "Cargando..."` para que
 * TalkBack anuncie el estado de carga correctamente.
 */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { contentDescription = "Cargando tu información personal…" },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: saludo + fecha
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(72.dp))

        // Tarjeta sesión de hoy
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(128.dp))

        // Widget racha
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(88.dp))

        // Resumen semanal
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(110.dp))

        // Acceso rápido (3 tarjetas)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(modifier = Modifier.weight(1f).height(80.dp))
            ShimmerBox(modifier = Modifier.weight(1f).height(80.dp))
            ShimmerBox(modifier = Modifier.weight(1f).height(80.dp))
        }
    }
}

/**
 * Caja con efecto shimmer (gradiente animado horizontal).
 *
 * Usa [rememberInfiniteTransition] para mover el gradiente de izquierda a
 * derecha en bucle — simula el efecto de luz que indica carga en curso.
 *
 * @param modifier Modificador externo (debe incluir tamaño).
 * @param shape    Forma del recorte (por defecto 12 dp redondeado).
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape       = RoundedCornerShape(12.dp)
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        MaterialTheme.colorScheme.surfaceVariant
    )

    val transition   = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1200f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset(translateAnim - 600f, 0f),
        end    = Offset(translateAnim,          0f)
    )

    Box(modifier = modifier.background(brush = brush, shape = shape))
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Skeleton — Claro", showBackground = true)
@Preview(name = "Skeleton — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardSkeletonPreview() {
    EternaMenteTheme {
        Surface { DashboardSkeleton() }
    }
}
