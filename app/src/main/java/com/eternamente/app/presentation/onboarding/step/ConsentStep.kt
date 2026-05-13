package com.eternamente.app.presentation.onboarding.step

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eternamente.app.presentation.component.EternaFullWidthButton
import com.eternamente.app.presentation.onboarding.ConsentFormState

// ══════════════════════════════════════════════════════════════════════════════
// Texto del consentimiento — seccionado para LazyColumn
// ══════════════════════════════════════════════════════════════════════════════

private data class ConsentSection(val title: String, val body: String)

private val consentSections = listOf(
    ConsentSection(
        title = "Propósito de EternaMente",
        body  = """EternaMente es una aplicación de entrenamiento cognitivo diseñada exclusivamente para personas mayores de 60 años. Su objetivo es ayudar a mantener y fortalecer las capacidades cognitivas a través de juegos y actividades científicamente respaldadas. La app evalúa seis dominios cognitivos: memoria de trabajo, atención sostenida, funciones ejecutivas, fluidez del lenguaje, orientación y velocidad de procesamiento.

La aplicación genera indicadores de riesgo cognitivo utilizando modelos de inteligencia artificial que se ejecutan completamente en su dispositivo, sin enviar datos a ningún servidor externo."""
    ),
    ConsentSection(
        title = "EternaMente NO es un instrumento de diagnóstico",
        body  = """IMPORTANTE: Los resultados, puntuaciones y cualquier indicador generado por EternaMente NO constituyen un diagnóstico médico, clínico ni neuropsicológico. La aplicación ha sido diseñada como una herramienta de bienestar personal y seguimiento preventivo, no como reemplazo de la evaluación profesional.

Si usted, un familiar o cuidador tiene preocupaciones sobre su salud cognitiva, le instamos a consultar con un médico, neurólogo o neuropsicólogo certificado. Ninguna puntuación de esta aplicación debe utilizarse para tomar decisiones médicas, farmacológicas o de tratamiento sin supervisión profesional."""
    ),
    ConsentSection(
        title = "Recopilación de datos personales",
        body  = """Al utilizar EternaMente usted consiente que la aplicación almacene en su dispositivo:

• Nombre, edad, género y nivel de escolaridad (para normalización de puntuaciones según cohorte demográfica).
• Resultados detallados de los juegos cognitivos: tiempos de reacción, precisión, errores, nivel de dificultad alcanzado.
• Fechas, duración y tipo de cada sesión de entrenamiento.
• Indicadores de riesgo cognitivo generados localmente (riskScore, alertLevel, dominios en observación).
• Configuración de accesibilidad personal.

Estos datos se almacenan cifrados con AES-256 en la base de datos local del dispositivo. Únicamente el correo electrónico es procesado por Firebase Authentication para la autenticación segura de la cuenta."""
    ),
    ConsentSection(
        title = "Privacidad y seguridad",
        body  = """EternaMente ha sido diseñada aplicando los principios de privacidad por diseño (Privacy by Design). Las siguientes garantías están implementadas técnicamente y no pueden ser desactivadas:

• La inferencia de riesgo cognitivo se realiza on-device. Los modelos de TensorFlow Lite procesan sus datos localmente; ningún dato cognitivo se transmite a servidores externos.
• Los datos almacenados en el dispositivo están cifrados con SQLCipher (AES-256-CBC). Sin la clave protegida por el sistema Android Keystore, los datos son ilegibles incluso con acceso físico al dispositivo.
• Las notificaciones push (recordatorios de sesión) se gestionan vía Firebase Cloud Messaging. Únicamente se transmite el token de notificación, no datos cognitivos.
• Ningún dato de rendimiento, historial de sesiones ni predicciones de riesgo se comparte con terceros, aseguradoras, empleadores ni entidades gubernamentales."""
    ),
    ConsentSection(
        title = "Sus derechos sobre sus datos",
        body  = """Como titular de los datos, usted tiene derecho a:

• Acceder en cualquier momento a todos sus datos desde la sección "Perfil → Mis datos".
• Exportar su historial completo en formato legible (CSV o PDF) para compartirlo con su médico.
• Rectificar cualquier dato de perfil incorrecto desde "Perfil → Editar".
• Eliminar permanentemente su cuenta y todos los datos asociados desde "Ajustes → Eliminar cuenta". Esta acción es irreversible.
• Retirar este consentimiento en cualquier momento eliminando su cuenta.

La retención de datos está limitada a 12 meses de historial de sesiones. Los registros anteriores se eliminan automáticamente de forma periódica."""
    ),
    ConsentSection(
        title = "Uso responsable",
        body  = """Al continuar, usted se compromete a:

• Utilizar EternaMente como herramienta de bienestar complementaria, no como sustituto de atención médica profesional.
• Comunicar cualquier cambio preocupante en sus capacidades cognitivas a un profesional de la salud, independientemente de los resultados de la app.
• No compartir su cuenta con otras personas, ya que los datos de perfil y normalización son individuales.

Esta aplicación está indicada para personas mayores de 60 años. Si es usted un cuidador que configura la aplicación para un familiar, asegúrese de contar con su consentimiento explícito y de leer este documento en su presencia."""
    )
)

// ══════════════════════════════════════════════════════════════════════════════
// Composable
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Paso 3 del onboarding — Consentimiento informado.
 *
 * **Mecánica del scroll-to-enable:**
 * Usa [derivedStateOf] para calcular si el último ítem del [LazyColumn] es visible.
 * Cuando el usuario llega al final, se notifica a [onScrolledToEnd] y el estado
 * persiste en el ViewModel (no se "desvuelve" al hacer scroll hacia arriba).
 *
 * **Botón "Acepto":** habilitado solo cuando:
 * 1. El usuario ha llegado al final del texto ([ConsentFormState.scrolledToEnd]).
 * 2. El checkbox "He leído y entiendo…" está marcado ([ConsentFormState.checkboxChecked]).
 *
 * **Persistencia en Room:** el timestamp se registra en `users.consent_given_at`
 * al completar el onboarding vía [OnboardingViewModel.completeOnboarding].
 */
@Composable
fun ConsentStep(
    consentForm: ConsentFormState,
    onScrolledToEnd: () -> Unit,
    onCheckboxChanged: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    val listState = rememberLazyListState()

    // Detectar si el último ítem de la lista es visible
    val hasScrolledToEnd by remember {
        derivedStateOf {
            val info         = listState.layoutInfo
            val lastVisible  = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            val lastIndex    = info.totalItemsCount - 1
            // El último ítem es visible y su borde inferior está dentro del viewport
            lastVisible.index >= lastIndex &&
                (lastVisible.offset + lastVisible.size) <= info.viewportEndOffset + 16
        }
    }

    // Notificar al ViewModel cuando se llega al final (solo la primera vez que ocurre)
    LaunchedEffect(hasScrolledToEnd) {
        if (hasScrolledToEnd) onScrolledToEnd()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Texto del consentimiento (scrollable) ──────────────────────────────
        LazyColumn(
            state           = listState,
            modifier        = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding  = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Título principal
            item(key = "header") {
                Text(
                    text     = "Consentimiento informado",
                    style    = MaterialTheme.typography.headlineSmall,
                    color    = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Lee con atención antes de continuar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            // Secciones del consentimiento
            consentSections.forEachIndexed { index, section ->
                item(key = "section_$index") {
                    Text(
                        text     = section.title,
                        style    = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color    = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = section.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(20.dp))
                    if (index < consentSections.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 20.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            // Indicador de llegada al final
            item(key = "scroll_indicator") {
                if (!consentForm.scrolledToEnd) {
                    Text(
                        text  = "↓ Continúa leyendo para habilitar el botón",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .semantics { contentDescription = "Continúa desplazándote hacia abajo para poder aceptar" }
                    )
                }
            }

            // Checkbox — último ítem; cuando es visible se habilita el botón
            item(key = "checkbox") {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value    = consentForm.checkboxChecked,
                            onValueChange = onCheckboxChanged,
                            role     = Role.Checkbox,
                            enabled  = consentForm.scrolledToEnd
                        )
                        .padding(vertical = 8.dp)
                        .semantics {
                            contentDescription = "Casilla de verificación: He leído y entiendo que esta app no reemplaza el diagnóstico médico. ${if (consentForm.checkboxChecked) "Marcada" else "Sin marcar"}"
                        },
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked  = consentForm.checkboxChecked,
                        onCheckedChange = null,  // Manejado por el toggleable del Row
                        enabled  = consentForm.scrolledToEnd
                    )
                    Text(
                        text     = "  He leído y entiendo que esta aplicación no reemplaza el diagnóstico médico profesional.",
                        style    = MaterialTheme.typography.bodyLarge,
                        color    = if (consentForm.scrolledToEnd)
                            MaterialTheme.colorScheme.onBackground
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Botón fijo al final ────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            EternaFullWidthButton(
                text               = "Acepto y continúo",
                onClick            = onNext,
                enabled            = consentForm.canAccept,
                contentDescription = when {
                    !consentForm.scrolledToEnd -> "Lee el documento completo para habilitar este botón"
                    !consentForm.checkboxChecked -> "Marca la casilla para habilitar este botón"
                    else -> "Acepto el consentimiento informado y continúo"
                }
            )
        }
    }
}
