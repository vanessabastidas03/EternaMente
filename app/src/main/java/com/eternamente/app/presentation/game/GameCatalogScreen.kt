package com.eternamente.app.presentation.game

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.ui.theme.EternaMenteTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// ── Estado del juego en el catálogo ──────────────────────────────────────────

enum class GameStatus { AVAILABLE, COMPLETED_TODAY, IN_SESSION }

data class CatalogGameEntry(
    val definition: GameDefinition,
    val status: GameStatus = GameStatus.AVAILABLE,
    val isInTodaySession: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class GameCatalogViewModel @Inject constructor(
    private val gameResultRepository: GameResultRepository,
    private val sessionRepository: SessionRepository,
    private val userPrefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _games = MutableStateFlow<List<CatalogGameEntry>>(emptyList())
    val games: StateFlow<List<CatalogGameEntry>> = _games.asStateFlow()

    // Juegos de la sesión de hoy (selección simplificada para MVP)
    private val todaySessionGameIds = setOf(
        "memory_match", "stroop", "temporal_orientation", "verbal_fluency", "digit_span"
    )

    init { loadGames() }

    private fun loadGames() {
        viewModelScope.launch {
            val userId = userPrefsRepository.getCurrentUserId()
            val zone   = ZoneId.systemDefault()
            val now    = LocalDate.now(zone)
            val dayStart = now.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd   = dayStart + 86_400_000L - 1

            val completedToday = if (userId != null) {
                // Juegos con resultados guardados hoy
                try {
                    val result = gameResultRepository.getLatestResults(userId, 50)
                    (result as? com.eternamente.app.core.Result.Success)?.data
                        ?.filter { it.sessionId.isNotEmpty() }
                        ?.map { it.gameId }
                        ?.toSet() ?: emptySet()
                } catch (e: Exception) { emptySet() }
            } else emptySet<String>()

            _games.value = ALL_GAME_DEFINITIONS.map { def ->
                val completedT = def.gameId in completedToday
                val inSession  = def.gameId in todaySessionGameIds
                CatalogGameEntry(
                    definition = def,
                    status = when {
                        completedT -> GameStatus.COMPLETED_TODAY
                        inSession  -> GameStatus.IN_SESSION
                        else       -> GameStatus.AVAILABLE
                    },
                    isInTodaySession = inSession
                )
            }
        }
    }
}

// ── Pantalla principal ────────────────────────────────────────────────────────

@Composable
fun GameCatalogScreen(
    innerPadding: PaddingValues,
    onNavigateToGame: (gameId: String) -> Unit
) {
    val viewModel: GameCatalogViewModel = hiltViewModel()
    val games by viewModel.games.collectAsState()
    var selectedDomain by remember { mutableStateOf<CognitiveDomain?>(null) }

    val filtered = if (selectedDomain == null) games
                   else games.filter { it.definition.domain == selectedDomain }

    Surface(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Encabezado ────────────────────────────────────────────────────
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Juegos cognitivos",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Selecciona un juego para comenzar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── Chips de filtro por dominio ────────────────────────────────────
            val domains = CognitiveDomain.entries
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedDomain == null,
                        onClick  = { selectedDomain = null },
                        label    = { Text("Todos") },
                        modifier = Modifier.semantics { contentDescription = "Filtro: todos los juegos" }
                    )
                }
                items(domains) { domain ->
                    FilterChip(
                        selected = selectedDomain == domain,
                        onClick  = { selectedDomain = if (selectedDomain == domain) null else domain },
                        label    = { Text("${domain.catalogEmoji()} ${domain.catalogLabel()}") },
                        modifier = Modifier.semantics { contentDescription = "Filtro: ${domain.catalogLabel()}" }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Leyenda de la sesión de hoy ────────────────────────────────────
            if (games.any { it.isInTodaySession }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⭐", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Resaltados = juegos de tu sesión de hoy",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Grid de GameCards ─────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.definition.gameId }) { entry ->
                    GameCard(entry = entry, onClick = { onNavigateToGame(entry.definition.gameId) })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── GameCard ─────────────────────────────────────────────────────────────────

@Composable
fun GameCard(entry: CatalogGameEntry, onClick: () -> Unit) {
    val def     = entry.definition
    val isToday = entry.isInTodaySession
    val isDone  = entry.status == GameStatus.COMPLETED_TODAY

    val borderColor by animateColorAsState(
        targetValue = when {
            isDone  -> MaterialTheme.colorScheme.secondary
            isToday -> MaterialTheme.colorScheme.primary
            else    -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        },
        animationSpec = tween(300),
        label = "card_border"
    )

    val cardBg = when {
        isDone  -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else    -> Color.Transparent
    }

    ElevatedCard(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${def.name}. ${def.shortDescription}. Estado: ${
                    when (entry.status) {
                        GameStatus.COMPLETED_TODAY -> "completado hoy"
                        GameStatus.IN_SESSION      -> "en la sesión de hoy"
                        GameStatus.AVAILABLE       -> "disponible"
                    }
                }"
            },
        colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(if (isToday) 4.dp else 1.dp)
    ) {
        Box {
            // Fondo de estado
            if (cardBg != Color.Transparent) {
                Box(Modifier.matchParentSize().background(cardBg))
            }
            // Indicador de sesión de hoy
            if (isToday) {
                Text("⭐", fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }

            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Emoji del juego
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Text(def.emoji, fontSize = 36.sp, modifier = Modifier.semantics { contentDescription = "" })
                    if (isDone) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Completado hoy",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.align(Alignment.BottomEnd).size(18.dp))
                    }
                }

                // Nombre del juego
                Text(def.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2)

                // Dominio cognitivo
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text  = "${def.domain.catalogEmoji()} ${def.domainLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Descripción corta
                Text(def.shortDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2)
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "GameCard disponible", showBackground = true)
@Composable
private fun GameCardAvailablePreview() {
    EternaMenteTheme {
        Surface(Modifier.width(180.dp).padding(8.dp)) {
            GameCard(CatalogGameEntry(ALL_GAME_DEFINITIONS.first(), GameStatus.AVAILABLE, false)) {}
        }
    }
}

@Preview(name = "GameCard sesión hoy", showBackground = true)
@Composable
private fun GameCardSessionPreview() {
    EternaMenteTheme {
        Surface(Modifier.width(180.dp).padding(8.dp)) {
            GameCard(CatalogGameEntry(ALL_GAME_DEFINITIONS[2], GameStatus.IN_SESSION, true)) {}
        }
    }
}

@Preview(name = "GameCard completado hoy", showBackground = true)
@Preview(name = "GameCard dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GameCardDonePreview() {
    EternaMenteTheme {
        Surface(Modifier.width(180.dp).padding(8.dp)) {
            GameCard(CatalogGameEntry(ALL_GAME_DEFINITIONS[4], GameStatus.COMPLETED_TODAY, true)) {}
        }
    }
}
