package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.repository.GameResultRepository

/**
 * Validates and persists a [GameResult] produced at the end of a mini-game.
 *
 * Domain validations applied before persistence:
 * - [GameResult.scoreNormalized] must be in the range [0.0, 100.0].
 * - [GameResult.accuracyPct] must be in the range [0.0, 100.0].
 * - [GameResult.reactionTimeMsAvg] must be positive.
 * - [GameResult.errorsCount] must be non-negative.
 * - [GameResult.difficultyLevel] must be ≥ 1.
 *
 * @property gameResultRepository Handles game result persistence.
 */
class SaveGameResultUseCase(
    private val gameResultRepository: GameResultRepository
) {

    /**
     * Validates and stores a single game result.
     *
     * @param result The [GameResult] to validate and persist.
     * @return [Result.Success] with the stored [GameResult], or [Result.Error] on
     *   validation failure (illegal field values) or I/O failure.
     */
    suspend operator fun invoke(result: GameResult): Result<GameResult> {
        TODO("Not yet implemented")
    }
}
