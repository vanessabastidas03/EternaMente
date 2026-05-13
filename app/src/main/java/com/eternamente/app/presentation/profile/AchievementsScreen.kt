package com.eternamente.app.presentation.profile

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.GamificationProfile
import com.eternamente.app.ui.theme.EternaMenteTheme

// ── Badge emoji lookup ─────────────────────────────────────────────────────────

private val BADGE_EMOJIS = mapOf(
    Badge.FIRST_STEP         to "🌟",
    Badge.WEEK_WARRIOR       to "⚔️",
    Badge.CONSISTENT         to "🎯",
    Badge.CONSISTENCY_MASTER to "👑",
    Badge.MEMORY_ACE         to "🧠",
    Badge.ATTENTION_CHAMPION to "🦅",
    Badge.DOMAIN_EXPLORER    to "🗺️",
    Badge.LEVEL_MAX          to "🚀",
    Badge.SPEED_DEMON        to "⚡",
    Badge.FULL_SPRINT        to "🏃",
    Badge.COMEBACK           to "🦋",
    Badge.FIRST_REPORT       to "📊",
    Badge.EARLY_ADOPTER      to "🌱"
)

// ── Screen ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    innerPadding: PaddingValues,
    onNavigateBack: () -> Unit
) {
    val viewModel: AchievementsViewModel = hiltViewModel()
    val state       by viewModel.state.collectAsState()
    val liveProfile by viewModel.liveProfile.collectAsState()

    // Detect newly unlocked badges in real time
    var prevBadges     by remember { mutableStateOf<Set<Badge>>(emptySet()) }
    var celebrateBadge by remember { mutableStateOf<Badge?>(null) }

    LaunchedEffect(liveProfile) {
        val current = liveProfile?.badges?.toSet() ?: return@LaunchedEffect
        val newly   = current - prevBadges
        if (prevBadges.isNotEmpty() && newly.isNotEmpty()) {
            celebrateBadge = newly.first()
        }
        prevBadges = current
    }

    var selectedBadge by remember { mutableStateOf<Badge?>(null) }
    val sheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = Modifier.padding(innerPadding),
        topBar   = {
            TopAppBar(
                title = {
                    Text(
                        "Logros y medallas",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { scaffoldPadding ->

        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(scaffoldPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp)
        ) {
            state.profile?.let { profile ->
                AchievementsSummaryRow(profile)
                Spacer(Modifier.height(16.dp))
            }

            LazyVerticalGrid(
                columns               = GridCells.Fixed(3),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding        = PaddingValues(bottom = 32.dp)
            ) {
                items(Badge.entries.toList(), key = { it.name }) { badge ->
                    val isEarned = state.profile?.hasBadge(badge) == true
                    val progress = state.badgeProgress[badge] ?: 0
                    BadgeCell(
                        badge    = badge,
                        isEarned = isEarned,
                        progress = progress,
                        onClick  = { selectedBadge = badge }
                    )
                }
            }
        }

        // Confetti overlay for new badge unlocks
        celebrateBadge?.let { badge ->
            ConfettiOverlay(badge = badge, onDone = { celebrateBadge = null })
        }
    }

    // Detail BottomSheet
    selectedBadge?.let { badge ->
        val isEarned = state.profile?.hasBadge(badge) == true
        val progress = state.badgeProgress[badge] ?: 0
        ModalBottomSheet(
            onDismissRequest = { selectedBadge = null },
            sheetState       = sheetState
        ) {
            BadgeDetailSheet(badge, isEarned, progress)
        }
    }
}

// ── Summary row ────────────────────────────────────────────────────────────────

@Composable
private fun AchievementsSummaryRow(profile: GamificationProfile) {
    val earned = profile.badges.size
    val total  = Badge.entries.size
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "$earned / $total logros",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${profile.totalPoints} puntos totales",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Text(
                text  = "${(earned * 100f / total.coerceAtLeast(1)).toInt()}%",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Badge cell ─────────────────────────────────────────────────────────────────

@Composable
private fun BadgeCell(
    badge: Badge,
    isEarned: Boolean,
    progress: Int,
    onClick: () -> Unit
) {
    val emoji       = BADGE_EMOJIS[badge] ?: "🏅"
    val bgColor     = if (isEarned) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val borderColor = if (isEarned) MaterialTheme.colorScheme.secondary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics {
                contentDescription =
                    "${badge.displayName}: ${if (isEarned) "obtenida" else "$progress% completado"}"
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text     = if (isEarned) emoji else "🔒",
            fontSize = 32.sp,
            modifier = Modifier.alpha(if (isEarned) 1f else 0.35f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text      = badge.displayName,
            style     = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isEarned) FontWeight.Bold else FontWeight.Normal
            ),
            textAlign = TextAlign.Center,
            color     = if (isEarned) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines  = 2
        )
        if (!isEarned && progress > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress   = { progress / 100f },
                modifier   = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// ── Badge detail sheet ─────────────────────────────────────────────────────────

@Composable
private fun BadgeDetailSheet(badge: Badge, isEarned: Boolean, progress: Int) {
    val emoji = BADGE_EMOJIS[badge] ?: "🏅"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text     = if (isEarned) emoji else "🔒",
            fontSize = 64.sp,
            modifier = Modifier.alpha(if (isEarned) 1f else 0.4f)
        )
        Spacer(Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isEarned) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text     = if (isEarned) "✓ Obtenida" else "Bloqueada",
                style    = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color    = if (isEarned) MaterialTheme.colorScheme.onSecondaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text      = badge.displayName,
            style     = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = badge.description,
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isEarned && progress > 0) {
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Progreso", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$progress%", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress   = { progress / 100f },
                modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Confetti overlay ───────────────────────────────────────────────────────────

@Composable
private fun ConfettiOverlay(badge: Badge, onDone: () -> Unit) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/confetti.json")
    )
    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = 1,
        isPlaying   = true
    )

    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(400))
        alpha.animateTo(1f, tween(300))
        kotlinx.coroutines.delay(2_500)
        alpha.animateTo(0f, tween(500))
        onDone()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress    = { lottieProgress },
                modifier    = Modifier.fillMaxSize()
            )
        }

        Card(
            modifier  = Modifier.scale(scale.value).alpha(alpha.value).padding(32.dp),
            shape     = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🏆 ¡Nuevo logro!", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                Text(BADGE_EMOJIS[badge] ?: "🏅", fontSize = 56.sp)
                Text(badge.displayName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(badge.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

// ── Previews ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BadgeCellPreview() {
    EternaMenteTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { BadgeCell(Badge.FIRST_STEP, isEarned = true,  progress = 100, onClick = {}) }
            Box(Modifier.weight(1f)) { BadgeCell(Badge.WEEK_WARRIOR, isEarned = false, progress = 43, onClick = {}) }
            Box(Modifier.weight(1f)) { BadgeCell(Badge.MEMORY_ACE,  isEarned = false, progress = 0,  onClick = {}) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BadgeSheetPreview() {
    EternaMenteTheme { Surface { BadgeDetailSheet(Badge.SPEED_DEMON, isEarned = false, progress = 72) } }
}
