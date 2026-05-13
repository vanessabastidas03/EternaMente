package com.eternamente.app.presentation.onboarding.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eternamente.app.presentation.component.EternaFullWidthButton

/**
 * Paso 1 del onboarding — Pantalla de bienvenida.
 *
 * Muestra el ícono de la app, el mensaje de bienvenida y los tres pilares
 * diferenciales de EternaMente. El botón atrás del sistema está DESHABILITADO
 * en este paso (gestionado por [BackHandler] en [OnboardingScreen]).
 *
 * Tipografía: mínimo 18 sp en todo el contenido visible.
 */
@Composable
fun WelcomeStep(onNext: () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        Spacer(Modifier.height(16.dp))

        // ── Ícono principal ───────────────────────────────────────────────────
        Icon(
            imageVector        = Icons.Filled.Psychology,
            contentDescription = "Ícono de EternaMente: mente con conexiones cognitivas",
            modifier           = Modifier.size(96.dp),
            tint               = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(28.dp))

        // ── Título ────────────────────────────────────────────────────────────
        Text(
            text      = "¡Bienvenida a EternaMente!",
            style     = MaterialTheme.typography.headlineLarge.copy(
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            color     = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier  = Modifier.semantics {
                contentDescription = "Bienvenida a EternaMente"
            }
        )

        Spacer(Modifier.height(12.dp))

        // ── Subtítulo ─────────────────────────────────────────────────────────
        Text(
            text      = "Cuida tu mente jugando todos los días",
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        // ── Bullets de propuesta de valor ─────────────────────────────────────
        WelcomeBullet(
            icon               = Icons.Filled.SportsEsports,
            text               = "Juegos diseñados para tu mente",
            contentDescription = "Juegos cognitivos diseñados para adultos mayores"
        )

        Spacer(Modifier.height(20.dp))

        WelcomeBullet(
            icon               = Icons.Filled.Timeline,
            text               = "Seguimiento inteligente de tu progreso",
            contentDescription = "Seguimiento inteligente y personalizado de tu evolución cognitiva"
        )

        Spacer(Modifier.height(20.dp))

        WelcomeBullet(
            icon               = Icons.Filled.Lock,
            text               = "Completamente privado y seguro",
            contentDescription = "Completamente privado y seguro: tus datos nunca salen de tu dispositivo"
        )

        Spacer(Modifier.height(48.dp))

        // ── Botón de acción ───────────────────────────────────────────────────
        EternaFullWidthButton(
            text               = "Comenzar",
            onClick            = onNext,
            contentDescription = "Comenzar el proceso de registro en EternaMente"
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WelcomeBullet(
    icon: ImageVector,
    text: String,
    contentDescription: String
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,  // Semántica en el Row
            modifier           = Modifier.size(32.dp),
            tint               = MaterialTheme.colorScheme.secondary
        )

        Text(
            text     = "  $text",
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onBackground
        )
    }
}
