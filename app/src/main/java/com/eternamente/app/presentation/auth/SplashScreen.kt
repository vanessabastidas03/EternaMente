package com.eternamente.app.presentation.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Pantalla de splash que determina el destino inicial leyendo DataStore.
 *
 * Destinos:
 * - Sin cuenta → [onNavigateToRegister]
 * - Con cuenta, onboarding incompleto → [onNavigateToOnboarding]
 * - Con cuenta, onboarding completo → [onNavigateToDashboard]
 */
@Composable
fun SplashScreen(
    innerPadding: PaddingValues,
    onNavigateToRegister: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToLogin: () -> Unit = {}
) {
    val viewModel: SplashViewModel = hiltViewModel()
    val destination by viewModel.destination.collectAsState()

    LaunchedEffect(destination) {
        when (destination) {
            SplashViewModel.Destination.Register   -> onNavigateToRegister()
            SplashViewModel.Destination.Login      -> onNavigateToLogin()
            SplashViewModel.Destination.Onboarding -> onNavigateToOnboarding()
            SplashViewModel.Destination.Idle       -> Unit // Esperando
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        color    = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Pantalla de carga de EternaMente" },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector        = Icons.Filled.Psychology,
                    contentDescription = "Logo EternaMente",
                    modifier           = Modifier.size(96.dp),
                    tint               = MaterialTheme.colorScheme.onPrimary
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text      = "EternaMente",
                    style     = MaterialTheme.typography.headlineLarge,
                    color     = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text      = "Cuida tu mente jugando",
                    style     = MaterialTheme.typography.bodyLarge,
                    color     = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(48.dp))

                CircularProgressIndicator(
                    modifier    = Modifier.size(36.dp),
                    color       = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
