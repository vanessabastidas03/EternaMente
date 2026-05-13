package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.core.getOrThrow
import com.eternamente.app.core.safeCall
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.model.GamificationProfile
import com.eternamente.app.domain.repository.GamificationRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Procesa una sesión completada y aplica todas las actualizaciones de gamificación:
 * 1. Puntos: base + bonus por precisión y dificultad.
 * 2. Racha: actualizada con la fecha de hoy.
 * 3. Medallas: evaluación de elegibilidad para todos los [Badge].
 */
class UpdateGamificationUseCase @Inject constructor(
    private val gamificationRepository: GamificationRepository
) {
    suspend operator fun invoke(
        session: CognitiveSession,
        results: List<GameResult>
    ): Result<GamificationProfile> = safeCall {
        val userId = session.userId
        require(userId.isNotBlank()) { "userId no puede estar vacío" }

        // ── 1. Calcular puntos ────────────────────────────────────────────────
        val basePoints    = BASE_POINTS_PER_SESSION
        val accuracyBonus = results.sumOf { r ->
            if (r.isPerfect) BONUS_POINTS_PERFECT_GAME else 0
        }
        val difficultyBonus = results.sumOf { r ->
            (r.difficultyLevel - 1).coerceAtLeast(0) * BONUS_POINTS_PER_DIFFICULTY_LEVEL
        }
        val totalPoints = basePoints + accuracyBonus + difficultyBonus
        gamificationRepository.addPoints(userId, totalPoints).getOrThrow()

        // ── 2. Actualizar racha ───────────────────────────────────────────────
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        gamificationRepository.updateStreak(userId, today).getOrThrow()

        // ── 3. Evaluar medallas ───────────────────────────────────────────────
        val profile = gamificationRepository.getProfile(userId).getOrThrow()
        evaluateBadges(userId, profile, results)

        // Devolver perfil actualizado
        gamificationRepository.getProfile(userId).getOrThrow()
    }

    private suspend fun evaluateBadges(
        userId: String,
        profile: GamificationProfile,
        results: List<GameResult>
    ) {
        // FIRST_STEP — primera sesión completada
        if (!profile.hasBadge(Badge.FIRST_STEP)) {
            gamificationRepository.unlockBadge(userId, Badge.FIRST_STEP)
        }
        // WEEK_WARRIOR — 7 días de racha
        if (profile.currentStreak >= 7 && !profile.hasBadge(Badge.WEEK_WARRIOR)) {
            gamificationRepository.unlockBadge(userId, Badge.WEEK_WARRIOR)
        }
        // MEMORY_ACE — 100% accuracy en memoria
        val hasPerfectMemory = results.any { it.domain.name == "MEMORY" && it.isPerfect }
        if (hasPerfectMemory && !profile.hasBadge(Badge.MEMORY_ACE)) {
            gamificationRepository.unlockBadge(userId, Badge.MEMORY_ACE)
        }
        // ATTENTION_CHAMPION — 100% accuracy en atención
        val hasPerfectAttention = results.any { it.domain.name == "ATTENTION" && it.isPerfect }
        if (hasPerfectAttention && !profile.hasBadge(Badge.ATTENTION_CHAMPION)) {
            gamificationRepository.unlockBadge(userId, Badge.ATTENTION_CHAMPION)
        }
    }

    companion object {
        const val BASE_POINTS_PER_SESSION        = 10
        const val BONUS_POINTS_PERFECT_GAME      = 5
        const val BONUS_POINTS_PER_DIFFICULTY_LEVEL = 2
    }
}
