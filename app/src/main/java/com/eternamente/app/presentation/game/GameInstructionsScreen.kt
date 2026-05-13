package com.eternamente.app.presentation.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.SessionType
import com.eternamente.app.domain.usecase.StartSessionUseCase
import com.eternamente.app.presentation.component.EternaFullWidthButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class GameStartViewModel @Inject constructor(
    private val startSessionUseCase: StartSessionUseCase,
    private val userPrefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _navEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<Pair<String, String>> = _navEvent.asSharedFlow()

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    fun createSessionAndStart() {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            val userId = userPrefsRepository.getCurrentUserId() ?: ""
            val sessionId = if (userId.isBlank()) {
                java.util.UUID.randomUUID().toString()
            } else {
                val result = startSessionUseCase(userId, SessionType.DAILY, System.currentTimeMillis())
                when (result) {
                    is com.eternamente.app.core.Result.Success -> result.data.id
                    is com.eternamente.app.core.Result.Error -> {
                        android.util.Log.w("GameStart", "Session fallback: ${result.exception.message}")
                        java.util.UUID.randomUUID().toString()
                    }
                }
            }
            _navEvent.emit(sessionId to userId)
            _isLoading.value = false
        }
    }
}

// ── Info de instrucciones por juego ──────────────────────────────────────────

private data class GameInfo(val emoji: String, val title: String, val domain: String, val description: String, val rules: List<String>)

private fun gameInfoFor(id: String) = when (id) {
    "memory_match"          -> GameInfo("🃏","Memorama de Pares","Memoria visual","Encuentra todos los pares de tarjetas iguales antes de que se acabe el tiempo.",listOf("Toca dos tarjetas para voltearlas","Par correcto: ✓ +10 pts","Par incorrecto: ✗ −2 pts, se vuelven a tapar","¡Encuentra todos los pares para ganar!"))
    "digit_span"            -> GameInfo("🔢","Secuencia de Números","Memoria de trabajo","Memoriza la secuencia de números y repítela en el mismo orden.",listOf("Los números aparecen uno a uno","Ingresa la secuencia con el teclado","Nivel 3+: repite en ORDEN INVERSO","5 rondas por sesión"))
    "flash_color"           -> GameInfo("🎨","Flash de Colores","Atención sostenida","Pulsa SOLO cuando aparezca el color objetivo. Ignora los demás.",listOf("Se define un color objetivo al inicio","Aparecen círculos de colores uno a uno","Pulsa si es el color objetivo ✓","NO pulses si es diferente ✗"))
    "trail_making"          -> GameInfo("🔗","Conecta los Puntos","Funciones ejecutivas","Conecta los puntos en orden numérico arrastrando el dedo.",listOf("25 puntos numerados en pantalla","Arrastra de número en número sin levantar el dedo","Nivel 3+: alterna números y letras (1→A→2→B…)","Sin límite de reinicios"))
    "naming_image"          -> GameInfo("🖼️","Nombra la Imagen","Lenguaje","Elige el nombre correcto de la imagen entre 4 opciones.",listOf("Aparece una imagen en pantalla","Selecciona el nombre correcto","Tienes 8 segundos por imagen","15 imágenes por sesión"))
    "verbal_fluency"        -> GameInfo("💬","Palabras en Categoría","Fluencia verbal","Escribe tantas palabras de la categoría como puedas en 60 segundos.",listOf("Se muestra una categoría (ej: Animales)","Escribe palabras una a una","Solo palabras válidas de la categoría","Más palabras = mejor puntuación"))
    "spot_diff"             -> GameInfo("🔍","Encuentra Diferencias","Atención dividida","Hay dos imágenes casi idénticas. Toca las diferencias en la imagen derecha.",listOf("Compara las dos imágenes","Toca en la imagen DERECHA las diferencias","3 minutos para encontrarlas todas","Cada diferencia correcta suma puntos"))
    "stroop"                -> GameInfo("🎭","Stroop de Colores","Control inhibitorio","Pulsa el botón del COLOR DE LA TINTA, ignora lo que dice la palabra.",listOf("Aparece una palabra en tinta de color diferente","Ignora el texto — responde al COLOR de la tinta","4 botones de colores abajo","¡Tiempo limitado por estímulo!"))
    "corsi_block"           -> GameInfo("⬜","Reproduce el Patrón","Memoria visoespacial","Memoriza la secuencia de cuadros iluminados y repítela.",listOf("Los cuadros se iluminan uno a uno","Observa el orden","Toca los cuadros EN EL MISMO ORDEN","La secuencia se alarga cada nivel"))
    "temporal_orientation"  -> GameInfo("📅","Orientación Temporal","Orientación","Responde 5 preguntas sobre la fecha y hora de hoy.",listOf("Preguntas sobre día, fecha, mes y año","4 opciones por pregunta","Elige la respuesta correcta","Sin límite de tiempo"))
    "clock_drawing"         -> GameInfo("🕐","Dibuja el Reloj","Visoespacial","Coloca las manecillas del reloj en la hora indicada.",listOf("Aparece una hora en texto","Toca para colocar la manecilla de HORAS","Luego toca para la manecilla de MINUTOS","Tolerancia ±15 grados"))
    "face_name"             -> GameInfo("👥","Caras y Nombres","Memoria asociativa","Memoriza qué nombre corresponde a cada cara.",listOf("Fase de estudio: 5 pares cara+nombre","30 segundos para memorizarlos","Fase de prueba: 4 opciones por cara","Sin límite de tiempo en la prueba"))
    "mental_calc"           -> GameInfo("➕","Cálculo Mental","Velocidad","Resuelve operaciones aritméticas lo más rápido posible.",listOf("10 operaciones por sesión","10 segundos por operación","Usa el teclado numérico","Suma, resta, multiplicación según nivel"))
    "prospective_memory"    -> GameInfo("⏰","Memoria Prospectiva","Memoria","Recuerda tocar el círculo verde SOLO cuando aparezca el objetivo.",listOf("Se muestra el emoji objetivo al inicio","Emojis aparecen uno a uno","Toca el círculo verde SOLO si ves el objetivo","Ignora todos los demás"))
    "reading_comprehension" -> GameInfo("📖","Lectura y Comprensión","Lenguaje","Lee el texto y responde las preguntas de comprensión.",listOf("Lee el párrafo completo","Pulsa 'He terminado' al acabar","Responde 3 preguntas de comprensión","Nivel 3+: el texto desaparece antes de las preguntas"))
    else                    -> GameInfo("🎮",id,"Cognitivo","Juego cognitivo.",listOf("Sigue las instrucciones en pantalla","¡Buena suerte!"))
}

// ── Composable ────────────────────────────────────────────────────────────────

/**
 * Pantalla de instrucciones con patrón de navegación CORRECTO.
 *
 * El botón NO llama navController.navigate() directamente desde un coroutine.
 * Flujo:
 *   Botón → ViewModel.createSessionAndStart() → emite navEvent (SharedFlow)
 *   LaunchedEffect → recibe navEvent → llama onStartGame() → navega
 *
 * Esto evita el crash "Cannot navigate while the NavController is not attached".
 */
@Composable
fun GameInstructionsScreen(
    innerPadding: PaddingValues,
    gameId: String,
    onStartGame: (sessionId: String, userId: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val viewModel: GameStartViewModel = hiltViewModel()
    val isLoading by viewModel.isLoading
    val info = remember(gameId) { gameInfoFor(gameId) }

    // CORRECCIÓN CRÍTICA: navegar dentro de LaunchedEffect, nunca desde lambda de coroutine
    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { (sessionId, userId) ->
            onStartGame(sessionId, userId)
        }
    }

    Surface(modifier = Modifier.fillMaxSize().padding(innerPadding), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(info.emoji, fontSize = 56.sp, modifier = Modifier.semantics { contentDescription = "" })
            Spacer(Modifier.height(8.dp))
            Text(info.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(top = 4.dp)) {
                Text(info.domain, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(info.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cómo jugar", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    info.rules.forEach { rule ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            Text(rule, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            EternaFullWidthButton(
                text = if (isLoading) "Preparando…" else "¡Comenzar juego!",
                onClick = { viewModel.createSessionAndStart() },
                enabled = !isLoading,
                isLoading = isLoading,
                contentDescription = "Iniciar ${info.title}"
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Volver al catálogo" }) {
                Text("← Volver al catálogo", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
