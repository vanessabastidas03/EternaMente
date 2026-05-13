package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.core.getOrThrow
import com.eternamente.app.core.safeCall
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.SessionType
import com.eternamente.app.domain.repository.SessionRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Creates and persists a new [CognitiveSession] in Room.
 *
 * Para el MVP no aplica la regla de baseline obligatorio para que los juegos
 * puedan iniciarse desde el catálogo sin restricciones.
 */
class StartSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(
        userId: String,
        type: SessionType = SessionType.DAILY,
        startTimestamp: Long = System.currentTimeMillis()
    ): Result<CognitiveSession> = safeCall {
        require(userId.isNotBlank()) { "userId no puede estar vacío" }

        val session = CognitiveSession(
            id              = UUID.randomUUID().toString(),
            userId          = userId,
            sessionDate     = startTimestamp,
            durationSeconds = null,
            type            = type,
            completed       = false
        )
        sessionRepository.saveSession(session).getOrThrow()
        session
    }
}
