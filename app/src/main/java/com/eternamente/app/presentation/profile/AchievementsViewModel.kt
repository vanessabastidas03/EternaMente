package com.eternamente.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.gamification.BadgeStats
import com.eternamente.app.domain.gamification.GamificationEngine
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.GamificationProfile
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.GamificationRepository
import com.eternamente.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AchievementsState(
    val profile: GamificationProfile? = null,
    val badgeProgress: Map<Badge, Int> = emptyMap(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val gamificationRepository: GamificationRepository,
    private val gameResultRepository: GameResultRepository,
    private val sessionRepository: SessionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val engine = GamificationEngine()

    private val _state = MutableStateFlow(AchievementsState())
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    /** Live profile used only to detect new badges in real time (reactive). */
    val liveProfile: StateFlow<GamificationProfile?> =
        userPreferencesRepository.preferences
            .flatMapLatest { prefs ->
                val uid = prefs.currentUserId ?: return@flatMapLatest flowOf(null)
                gamificationRepository.observeProfile(uid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { loadStats() }

    fun refresh() { loadStats() }

    private fun loadStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true)

            val userId = userPreferencesRepository.getCurrentUserId()
                ?: run { _state.value = _state.value.copy(isLoading = false); return@launch }

            val profile = gamificationRepository.getProfile(userId).getOrNull()
            val stats   = loadBadgeStats(userId)
            val progress = Badge.entries.associateWith { badge ->
                if (profile?.hasBadge(badge) == true) 100
                else engine.badgeProgress(badge, profile ?: emptyProfile(userId), stats)
            }

            _state.value = AchievementsState(
                profile     = profile,
                badgeProgress = progress,
                isLoading   = false
            )
        }
    }

    private suspend fun loadBadgeStats(userId: String): BadgeStats {
        val totalSessions = sessionRepository.countAllCompletedSessions(userId).getOrNull() ?: 0
        val memAbove90    = gameResultRepository.countMemoryGamesAboveAccuracy(userId, 90f).getOrNull() ?: 0
        val attPerfect    = gameResultRepository.countAttentionGamesPerfect(userId).getOrNull() ?: 0
        val uniqueDomains = gameResultRepository.countUniqueDomains(userId).getOrNull() ?: 0
        val maxDiff       = gameResultRepository.maxDifficultyReached(userId).getOrNull() ?: 1
        val flashMinRt    = gameResultRepository.flashColorMinRtMs(userId).getOrNull() ?: 0f
        val baseline      = if (sessionRepository.hasCompletedBaseline(userId).getOrNull() == true) 1 else 0
        return BadgeStats(
            totalSessionsCompleted    = totalSessions,
            memoryGamesAbove90        = memAbove90,
            attentionGamesPerfect     = attPerfect,
            uniqueDomainsTried        = uniqueDomains,
            maxDifficultyReached      = maxDiff,
            flashColorMinRtMs         = flashMinRt,
            baselineSessionsCompleted = baseline,
            longestGapDays            = 0,
            reportsGenerated          = 0
        )
    }

    private fun emptyProfile(userId: String) = GamificationProfile(
        userId = userId, totalPoints = 0, currentStreak = 0,
        maxStreak = 0, lastSessionDate = null, badges = emptyList()
    )

    private fun <T> com.eternamente.app.core.Result<T>.getOrNull(): T? =
        (this as? com.eternamente.app.core.Result.Success)?.data
}
