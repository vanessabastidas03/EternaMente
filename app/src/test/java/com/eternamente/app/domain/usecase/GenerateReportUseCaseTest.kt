package com.eternamente.app.domain.usecase

import app.cash.turbine.test
import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.model.SessionType
import com.eternamente.app.domain.model.User
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@DisplayName("GenerateReportUseCase")
class GenerateReportUseCaseTest {

    private val userRepository: UserRepository             = mockk()
    private val gameResultRepository: GameResultRepository = mockk()
    private val mlRepository: MlRepository                 = mockk()

    private lateinit var useCase: GenerateReportUseCase

    companion object {
        private const val USER_ID = "user-report"
    }

    private val testUser = User(
        id = USER_ID, email = "ana@test.com", name = "Ana García",
        age = 70, educationYears = 12, gender = "Mujer",
        createdAt = 1_000_000L, consentGivenAt = 1_000_001L
    )

    private fun buildGameResult(id: String = "r-001") = GameResult(
        id = id, sessionId = "s-001", gameId = "digit_span",
        domain = CognitiveDomain.MEMORY,
        scoreRaw = 75f, scoreNormalized = 75f,
        reactionTimeMsAvg = 1100f, reactionTimeMsP50 = 1050f,
        accuracyPct = 82f, errorsCount = 1, difficultyLevel = 2
    )

    private val testPrediction = MlPrediction(
        id = "pred-001", userId = USER_ID,
        predictionDate = 2_000_000L, riskScore = 0.25f,
        alertLevel = AlertLevel.NORMAL,
        domainsFlagged = emptyList(), explanation = "Sin riesgo detectado"
    )

    @BeforeEach
    fun setUp() {
        useCase = GenerateReportUseCase(userRepository, gameResultRepository, mlRepository)
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Reporte semanal con datos completos")
    inner class FullReport {

        @Test
        fun `devuelve CognitiveReport con datos del usuario y resultados`() = runTest {
            val results = (1..5).map { buildGameResult("r-$it") }
            coEvery { userRepository.getUserById(USER_ID) }            returns Result.Success(testUser)
            coEvery { gameResultRepository.getLatestResults(USER_ID, any()) } returns Result.Success(results)
            coEvery { mlRepository.getLatestPrediction(USER_ID) }      returns Result.Success(testPrediction)

            val outcome = useCase(USER_ID)

            assertInstanceOf(Result.Success::class.java, outcome)
            val report = (outcome as Result.Success).data
            assertEquals(testUser, report.user)
            assertEquals(5, report.recentResults.size)
            assertNotNull(report.latestPrediction)
            assertEquals(AlertLevel.NORMAL, report.latestPrediction!!.alertLevel)
        }

        @Test
        fun `generatedAt es un timestamp mayor a cero`() = runTest {
            coEvery { userRepository.getUserById(USER_ID) }            returns Result.Success(testUser)
            coEvery { gameResultRepository.getLatestResults(any(), any()) } returns Result.Success(emptyList())
            coEvery { mlRepository.getLatestPrediction(USER_ID) }      returns Result.Success(null)

            val outcome = useCase(USER_ID)

            assertTrue((outcome as Result.Success).data.generatedAt > 0)
        }

        @Test
        fun `llama a getUserById una sola vez`() = runTest {
            coEvery { userRepository.getUserById(USER_ID) }            returns Result.Success(testUser)
            coEvery { gameResultRepository.getLatestResults(any(), any()) } returns Result.Success(emptyList())
            coEvery { mlRepository.getLatestPrediction(USER_ID) }      returns Result.Success(null)

            useCase(USER_ID)

            coVerify(exactly = 1) { userRepository.getUserById(USER_ID) }
        }

        @Test
        fun `solicita RECENT_RESULTS_LIMIT resultados al repositorio`() = runTest {
            val limit = GenerateReportUseCase.RECENT_RESULTS_LIMIT
            coEvery { userRepository.getUserById(USER_ID) }                       returns Result.Success(testUser)
            coEvery { gameResultRepository.getLatestResults(USER_ID, limit) }     returns Result.Success(emptyList())
            coEvery { mlRepository.getLatestPrediction(USER_ID) }                 returns Result.Success(null)

            useCase(USER_ID)

            coVerify { gameResultRepository.getLatestResults(USER_ID, limit) }
        }

        @Test
        fun `Turbine — Flow de historial de predicciones emite lista con la predicción`() = runTest {
            val predictionList = listOf(testPrediction)
            coEvery { mlRepository.observePredictionHistory(USER_ID) } returns flowOf(predictionList)

            mlRepository.observePredictionHistory(USER_ID).test {
                val emitted = awaitItem()
                assertEquals(1, emitted.size)
                assertEquals(testPrediction, emitted.first())
                awaitComplete()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Reporte sin datos de ML")
    inner class ReportWithoutMl {

        @Test
        fun `latestPrediction null cuando el repositorio ML no tiene datos`() = runTest {
            coEvery { userRepository.getUserById(USER_ID) }            returns Result.Success(testUser)
            coEvery { gameResultRepository.getLatestResults(any(), any()) } returns Result.Success(emptyList())
            coEvery { mlRepository.getLatestPrediction(USER_ID) }      returns Result.Success(null)

            val outcome = useCase(USER_ID)

            assertNull((outcome as Result.Success).data.latestPrediction)
        }

        @Test
        fun `fallo en repositorio ML no impide generar el reporte`() = runTest {
            coEvery { userRepository.getUserById(USER_ID) }            returns Result.Success(testUser)
            coEvery { gameResultRepository.getLatestResults(any(), any()) } returns Result.Success(emptyList())
            coEvery { mlRepository.getLatestPrediction(USER_ID) }      returns Result.Error(Exception("ML DB error"))

            val outcome = useCase(USER_ID)

            // El reporte se genera con latestPrediction null
            assertInstanceOf(Result.Success::class.java, outcome)
            assertNull((outcome as Result.Success).data.latestPrediction)
        }

        @Test
        fun `recentResults vacío cuando el repositorio de resultados falla`() = runTest {
            coEvery { userRepository.getUserById(USER_ID) }                  returns Result.Success(testUser)
            coEvery { gameResultRepository.getLatestResults(any(), any()) }  returns
                Result.Error(Exception("query error"))
            coEvery { mlRepository.getLatestPrediction(USER_ID) }            returns Result.Success(null)

            val outcome = useCase(USER_ID)

            assertInstanceOf(Result.Success::class.java, outcome)
            assertTrue((outcome as Result.Success).data.recentResults.isEmpty())
        }

        @Test
        fun `Turbine — Flow de historial vacío emite lista vacía`() = runTest {
            coEvery { mlRepository.observePredictionHistory(USER_ID) } returns flowOf(emptyList())

            mlRepository.observePredictionHistory(USER_ID).test {
                val emitted = awaitItem()
                assertTrue(emitted.isEmpty())
                awaitComplete()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Errores bloqueantes")
    inner class BlockingErrors {

        @Test
        fun `usuario no encontrado en Room devuelve Result Error`() = runTest {
            coEvery { userRepository.getUserById(USER_ID) } returns
                Result.Error(NoSuchElementException("User not found: $USER_ID"))

            val outcome = useCase(USER_ID)

            assertInstanceOf(Result.Error::class.java, outcome)
        }

        @Test
        fun `error de usuario NO llama a repositorios de resultados ni ML`() = runTest {
            coEvery { userRepository.getUserById(USER_ID) } returns
                Result.Error(Exception("DB unavailable"))

            useCase(USER_ID)

            coVerify(exactly = 0) { gameResultRepository.getLatestResults(any(), any()) }
            coVerify(exactly = 0) { mlRepository.getLatestPrediction(any()) }
        }
    }
}
