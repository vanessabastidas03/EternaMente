package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.core.getOrThrow
import com.eternamente.app.core.safeCall
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.repository.GameResultRepository
import javax.inject.Inject

/**
 * Valida y persiste un [GameResult] producido al finalizar un mini-juego.
 *
 * Validaciones de dominio:
 * - [GameResult.scoreNormalized] en [0, 100].
 * - [GameResult.accuracyPct] en [0, 100].
 * - [GameResult.reactionTimeMsAvg] positivo.
 */
class SaveGameResultUseCase @Inject constructor(
    private val gameResultRepository: GameResultRepository
) {
    suspend operator fun invoke(result: GameResult): Result<GameResult> = safeCall {
        // Validaciones básicas de dominio
        require(result.scoreNormalized in 0f..100f) { "scoreNormalized fuera de rango: ${result.scoreNormalized}" }
        require(result.accuracyPct in 0f..100f)     { "accuracyPct fuera de rango: ${result.accuracyPct}" }

        gameResultRepository.saveGameResult(result).getOrThrow()
    }
}
