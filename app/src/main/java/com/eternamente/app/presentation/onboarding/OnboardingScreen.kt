package com.eternamente.app.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eternamente.app.presentation.onboarding.step.AccessibilityStep
import com.eternamente.app.presentation.onboarding.step.ConsentStep
import com.eternamente.app.presentation.onboarding.step.ProfileStep
import com.eternamente.app.presentation.onboarding.step.WelcomeStep

private const val STEP_FADE_MS = 300

/**
 * Pantalla raíz del flujo de onboarding de EternaMente.
 *
 * Gestiona los 4 pasos internamente con [OnboardingViewModel]. La navegación
 * entre pasos se anima con un fade de [STEP_FADE_MS] ms.
 *
 * **Back stack:** el sistema BackHandler está DESHABILITADO en el paso 1
 * (Welcome) y HABILITADO en los pasos 2, 3 y 4 para retroceder al paso anterior.
 *
 * @param innerPadding          Padding del Scaffold del NavGraph.
 * @param onNavigateToDashboard Navegar al panel principal al completar.
 * @param onNavigateToLogin     Acceso alternativo al login (enlace en Splash).
 */
@Composable
fun OnboardingScreen(
    innerPadding: PaddingValues,
    onNavigateToDashboard: () -> Unit,
    onNavigateToLogin: () -> Unit = {}
) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // Interceptar el botón atrás del sistema — deshabilitado en paso 1
    BackHandler(enabled = state.currentStep != OnboardingStep.Welcome) {
        viewModel.previousStep()
    }

    // Navegar al Dashboard cuando el onboarding se complete
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onNavigateToDashboard()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Barra superior (atrás + indicador de progreso) ────────────────
            OnboardingTopBar(
                step        = state.currentStep,
                onBack      = { viewModel.previousStep() }
            )

            // ── Contenido animado del paso actual ─────────────────────────────
            AnimatedContent(
                targetState  = state.currentStep,
                transitionSpec = {
                    fadeIn(tween(STEP_FADE_MS)) togetherWith fadeOut(tween(STEP_FADE_MS))
                },
                modifier     = Modifier.weight(1f),
                label        = "OnboardingStepTransition"
            ) { step ->
                when (step) {
                    OnboardingStep.Welcome ->
                        WelcomeStep(onNext = { viewModel.nextStep() })

                    OnboardingStep.Profile ->
                        ProfileStep(
                            profileForm        = state.profileForm,
                            onNameChanged      = viewModel::onNameChanged,
                            onAgeChanged       = viewModel::onAgeChanged,
                            onEducationChanged = viewModel::onEducationChanged,
                            onGenderChanged    = viewModel::onGenderChanged,
                            onNext             = { viewModel.nextStep() }
                        )

                    OnboardingStep.Consent ->
                        ConsentStep(
                            consentForm       = state.consentForm,
                            onScrolledToEnd   = viewModel::onScrolledToConsentEnd,
                            onCheckboxChanged = viewModel::onConsentCheckboxChanged,
                            onNext            = { viewModel.nextStep() }
                        )

                    OnboardingStep.Accessibility ->
                        AccessibilityStep(
                            accessibilityForm       = state.accessibilityForm,
                            onFontScaleChanged      = viewModel::onFontScaleChanged,
                            onHighContrastChanged   = viewModel::onHighContrastChanged,
                            onHapticFeedbackChanged = viewModel::onHapticFeedbackChanged,
                            onDarkModeChanged       = viewModel::onDarkModeChanged,
                            isLoading               = state.isLoading,
                            error                   = state.error,
                            onClearError            = viewModel::clearError,
                            onComplete              = { viewModel.nextStep() }
                        )
                }
            }
        }
    }
}

// ── Barra superior con botón atrás e indicador de progreso ────────────────────

@Composable
private fun OnboardingTopBar(
    step: OnboardingStep,
    onBack: () -> Unit
) {
    Column {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón atrás (oculto en paso 1)
            if (step != OnboardingStep.Welcome) {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Volver al paso anterior" }
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))  // Espacio para mantener alineación
            }

            Spacer(Modifier.weight(1f))

            Text(
                text  = "Paso ${step.index + 1} de ${OnboardingStep.TOTAL_STEPS}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Indicador de puntos de progreso
        StepDotsIndicator(currentIndex = step.index, total = OnboardingStep.TOTAL_STEPS)
    }
}

@Composable
private fun StepDotsIndicator(currentIndex: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "Paso ${currentIndex + 1} de $total"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val isActive = index <= currentIndex
            val isCurrent = index == currentIndex
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (isCurrent) 28.dp else 8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
            if (index < total - 1) Spacer(Modifier.width(6.dp))
        }
    }
}

/** Spinner de carga de pantalla completa (usado durante `completeOnboarding`). */
@Composable
private fun FullScreenLoading() {
    Box(
        modifier          = Modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier    = Modifier.size(56.dp),
            color       = MaterialTheme.colorScheme.primary,
            strokeWidth = 5.dp
        )
    }
}
