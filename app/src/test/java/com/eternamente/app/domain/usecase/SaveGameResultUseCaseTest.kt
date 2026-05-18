package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.repository.GameResultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@DisplayName("SaveGameResultUseCase")
class SaveGameResultUseCaseTest {

    private val gameResultRepository: GameResultRepository = mockk()
    private lateinit var useCase: SaveGameResultUseCase

    private fun buildResult(
        score: Float = 75f,
        accuracy: Float = 80f,
        rt: Float = 900f,
        sessionId: String = "session-abc"
    ) = GameResult(
        id                = "result-001",
        sessionId         = sessionId,
        gameId            = "digit_span",
        domain            = CognitiveDomain.MEMORY,
        scoreRaw          = score,
        scoreNormalized   = score,
        reactionTimeMsAvg = rt,
        reactionTimeMsP50 = rt,
        accuracyPct       = accuracy,
        errorsCount       = 2,
        difficultyLevel   = 3
    )

    @BeforeEach
    fun setUp() {
        useCase = SaveGameResultUseCase(gameResultRepository)
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Éxito")
    inner class Success {

        @Test
        fun `resultado válido se persiste y devuelve Success`() = runTest {
            val result = buildResult()
            coEvery { gameResultRepository.saveGameResult(result) } returns Result.Success(result)

            val outcome = useCase(result)

            assertInstanceOf(Result.Success::class.java, outcome)
            assertEquals(result, (outcome as Result.Success).data)
        }

        @Test
        fun `llama a saveGameResult exactamente una vez`() = runTest {
            val result = buildResult()
            coEvery { gameResultRepository.saveGameResult(any()) } returns Result.Success(result)

            useCase(result)

            coVerify(exactly = 1) { gameResultRepository.saveGameResult(result) }
        }

        @Test
        fun `resultado con score 0 es válido`() = runTest {
            val result = buildResult(score = 0f, accuracy = 0f)
            coEvery { gameResultRepository.saveGameResult(any()) } returns Result.Success(result)

            val outcome = useCase(result)

            assertTrue(outcome is Result.Success)
        }

        @Test
        fun `resultado con score 100 es válido`() = runTest {
            val result = buildResult(score = 100f, accuracy = 100f)
            coEvery { gameResultRepository.saveGameResult(any()) } returns Result.Success(result)

            val outcome = useCase(result)

            assertTrue(outcome is Result.Success)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Fallo en repositorio (sessionId no existe / error de BD)")
    inner class RepositoryError {

        @Test
        fun `error de repositorio se propaga como Result Error`() = runTest {
            val result = buildResult()
            coEvery { gameResultRepository.saveGameResult(any()) } returns
                Result.Error(Exception("sessionId 'session-abc' not found in Room"))

            val outcome = useCase(result)

            assertInstanceOf(Result.Error::class.java, outcome)
        }

        @Test
        fun `mensaje de error es el propagado por el repositorio`() = runTest {
            val errorMsg = "FOREIGN KEY constraint failed"
            coEvery { gameResultRepository.saveGameResult(any()) } returns
                Result.Error(Exception(errorMsg))

            val outcome = useCase(buildResult())

            assertEquals(errorMsg, (outcome as Result.Error).exception.message)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Datos fuera de rango")
    inner class OutOfRangeData {

        @Test
        fun `scoreNormalized negativo devuelve Error sin llamar al repositorio`() = runTest {
            val result = buildResult(score = -1f)

            val outcome = useCase(result)

            assertInstanceOf(Result.Error::class.java, outcome)
            coVerify(exactly = 0) { gameResultRepository.saveGameResult(any()) }
        }

        @Test
        fun `scoreNormalized mayor a 100 devuelve Error sin llamar al repositorio`() = runTest {
            val result = buildResult(score = 100.1f)

            val outcome = useCase(result)

            assertInstanceOf(Result.Error::class.java, outcome)
            coVerify(exactly = 0) { gameResultRepository.saveGameResult(any()) }
        }

        @Test
        fun `accuracyPct negativa devuelve Error sin llamar al repositorio`() = runTest {
            val result = buildResult(accuracy = -5f)

            val outcome = useCase(result)

            assertInstanceOf(Result.Error::class.java, outcome)
            coVerify(exactly = 0) { gameResultRepository.saveGameResult(any()) }
        }

        @Test
        fun `accuracyPct mayor a 100 devuelve Error sin llamar al repositorio`() = runTest {
            val result = buildResult(accuracy = 101f)

            val outcome = useCase(result)

            assertInstanceOf(Result.Error::class.java, outcome)
            coVerify(exactly = 0) { gameResultRepository.saveGameResult(any()) }
        }
    }
}
