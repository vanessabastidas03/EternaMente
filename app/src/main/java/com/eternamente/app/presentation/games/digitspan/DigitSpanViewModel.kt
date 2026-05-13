package com.eternamente.app.presentation.games.digitspan

import androidx.lifecycle.viewModelScope
import com.eternamente.app.data.local.preferences.UserPreferencesRepository
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult as DomainResult
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.usecase.SaveGameResultUseCase
import com.eternamente.app.domain.usecase.UpdateGamificationUseCase
import com.eternamente.app.presentation.games.engine.GameBaseViewModel
import com.eternamente.app.presentation.games.engine.GameEngine
import com.eternamente.app.presentation.games.engine.InputFeedback
import com.eternamente.app.presentation.games.engine.UserInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DigitSpanViewModel @Inject constructor(
    saveGameResultUseCase: SaveGameResultUseCase,
    updateGamificationUseCase: UpdateGamificationUseCase,
    sessionRepository: SessionRepository,
    userPreferencesRepository: UserPreferencesRepository
) : GameBaseViewModel<DigitSpanConfig, DigitSpanResult>(
    saveGameResultUseCase, updateGamificationUseCase, sessionRepository, userPreferencesRepository
) {
    private var _engine: DigitSpanEngine? = null
    override val engine: GameEngine<DigitSpanConfig, DigitSpanResult>
        get() = requireNotNull(_engine)

    private val _uiState = MutableStateFlow(DigitSpanUiState())
    val uiState: StateFlow<DigitSpanUiState> = _uiState.asStateFlow()

    fun initialize(config: DigitSpanConfig) {
        if (_engine != null) return
        val e = DigitSpanEngine(config)
        _engine = e
        viewModelScope.launch { e.uiState.collect { _uiState.value = it } }
    }

    fun startCountdown()         = _engine?.startCountdown()
    fun tapDigit(digit: Int): InputFeedback = onUserInput(UserInput.SelectOption(digit))
    fun deleteLast()             = onUserInput(UserInput.SelectOption(-1))   // -1 = borrar

    override fun buildDomainResult(engineResult: DigitSpanResult): DomainResult {
        val norm = with(engineResult) {
            val accuracy   = if (totalRounds > 0) correctRounds.toFloat() / totalRounds else 0f
            val spanScore  = maxCorrectSpan.toFloat() / 7f   // 7 = max span level 5
            (accuracy * 70f + spanScore * 30f).coerceIn(0f, 100f)
        }
        return DomainResult(
            id                = UUID.randomUUID().toString(),
            sessionId         = engineResult.sessionId,
            gameId            = engineResult.gameId,
            domain            = CognitiveDomain.MEMORY,
            scoreRaw          = engineResult.correctRounds.toFloat(),
            scoreNormalized   = norm,
            reactionTimeMsAvg = engineResult.metrics.mean,
            reactionTimeMsP50 = engineResult.metrics.median,
            accuracyPct       = engineResult.metrics.accuracyPct,
            errorsCount       = engineResult.metrics.errorCount,
            difficultyLevel   = engineResult.difficultyReached
        )
    }
}
