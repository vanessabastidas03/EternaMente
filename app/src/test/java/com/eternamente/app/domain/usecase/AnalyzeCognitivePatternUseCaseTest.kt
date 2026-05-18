package com.eternamente.app.domain.usecase

import com.eternamente.app.core.Result
import com.eternamente.app.domain.ml.CognitiveAnalysisResult
import com.eternamente.app.domain.ml.CognitiveAnalyzer
import com.eternamente.app.domain.ml.FeatureVector
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.MlPrediction
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.repository.SessionRepository
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
@DisplayName("AnalyzeCognitivePatternUseCase")
class AnalyzeCognitivePatternUseCaseTest {

    private val cognitiveAnalyzer: CognitiveAnalyzer       = mockk()
    private val gameResultRepository: GameResultRepository = mockk()
    private val mlRepository: MlRepository                 = mockk()
    private val sessionRepository: SessionRepository       = mockk()

    private lateinit var useCase: AnalyzeCognitivePatternUseCase

    companion object {
        private const val USER_ID = "user-42"
        // [0..3]=meanRt, [4..8]=accuracy, [9..10]=trend, [11]=completion, [12]=variability, [13]=delta
        private val NEUTRAL_RAW = floatArrayOf(
            1200f, 1100f, 1300f, 1000f,   // [0..3] meanRt
            0.80f, 0.75f, 0.70f, 0.85f, 0.90f, // [4..8] accuracy
            0.50f, 0.50f,                  // [9..10] trend
            0.80f, 0.20f, 0.10f            // [11..13] completion, variability, delta
        )
        private val NEUTRAL_VECTOR = FeatureVector(
            userId      = USER_ID,
            extractedAt = "2026-05-18",
            weeksBack   = 4,
            features    = NEUTRAL_RAW,
            normalized  = FloatArray(14) { 0.5f }
        )
    }

    private fun buildAnalysisResult(level: AlertLevel) = CognitiveAnalysisResult(
        userId          = USER_ID,
        analyzedAt      = 1_000_000L,
        featureVector   = NEUTRAL_VECTOR,
        anomalyScore    = when (level) { AlertLevel.NORMAL -> 0.2f; AlertLevel.WATCH -> 0.5f; AlertLevel.ALERT -> 0.8f },
        tfliteRiskScore = when (level) { AlertLevel.NORMAL -> 0.2f; AlertLevel.WATCH -> 0.5f; AlertLevel.ALERT -> 0.8f },
        alertLevel      = level,
        flaggedDomains  = emptyList(),
        explanation     = "Resultado: $level",
        modelUsed       = "statistical_fallback"
    )

    private fun buildSavedPrediction(level: AlertLevel) = MlPrediction(
        id             = "pred-001",
        userId         = USER_ID,
        predictionDate = 1_000_000L,
        riskScore      = when (level) { AlertLevel.NORMAL -> 0.2f; AlertLevel.WATCH -> 0.5f; AlertLevel.ALERT -> 0.8f },
        alertLevel     = level,
        domainsFlagged = emptyList(),
        explanation    = "Resultado: $level"
    )

    @BeforeEach
    fun setUp() {
        useCase = AnalyzeCognitivePatternUseCase(
            cognitiveAnalyzer, gameResultRepository, mlRepository, sessionRepository
        )
        // Enough sessions by default
        coEvery { sessionRepository.countAllCompletedSessions(USER_ID) } returns
            Result.Success(AnalyzeCognitivePatternUseCase.MIN_SESSIONS_REQUIRED)
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Alerta NORMAL")
    inner class NormalAlert {

        @Test
        fun `analiza y persiste predicción con alertLevel NORMAL`() = runTest {
            val analysis = buildAnalysisResult(AlertLevel.NORMAL)
            val saved    = buildSavedPrediction(AlertLevel.NORMAL)
            coEvery { cognitiveAnalyzer.analyze(USER_ID) }     returns analysis
            coEvery { mlRepository.savePrediction(any()) }     returns Result.Success(saved)

            val outcome = useCase(USER_ID)

            assertInstanceOf(Result.Success::class.java, outcome)
            assertEquals(AlertLevel.NORMAL, (outcome as Result.Success).data.alertLevel)
        }

        @Test
        fun `llama a analyze y savePrediction una vez cada uno`() = runTest {
            coEvery { cognitiveAnalyzer.analyze(USER_ID) }  returns buildAnalysisResult(AlertLevel.NORMAL)
            coEvery { mlRepository.savePrediction(any()) }  returns Result.Success(buildSavedPrediction(AlertLevel.NORMAL))

            useCase(USER_ID)

            coVerify(exactly = 1) { cognitiveAnalyzer.analyze(USER_ID) }
            coVerify(exactly = 1) { mlRepository.savePrediction(any()) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Alerta WATCH")
    inner class WatchAlert {

        @Test
        fun `devuelve predicción con alertLevel WATCH`() = runTest {
            coEvery { cognitiveAnalyzer.analyze(USER_ID) }  returns buildAnalysisResult(AlertLevel.WATCH)
            coEvery { mlRepository.savePrediction(any()) }  returns Result.Success(buildSavedPrediction(AlertLevel.WATCH))

            val outcome = useCase(USER_ID)

            assertEquals(AlertLevel.WATCH, (outcome as Result.Success).data.alertLevel)
        }

        @Test
        fun `riskScore de WATCH está en rango intermedio`() = runTest {
            coEvery { cognitiveAnalyzer.analyze(USER_ID) }  returns buildAnalysisResult(AlertLevel.WATCH)
            val pred = buildSavedPrediction(AlertLevel.WATCH)
            coEvery { mlRepository.savePrediction(any()) }  returns Result.Success(pred)

            val outcome = useCase(USER_ID)

            val riskScore = (outcome as Result.Success).data.riskScore
            assertTrue(riskScore in 0.3f..0.7f, "WATCH riskScore=$riskScore debe estar en [0.3, 0.7]")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Alerta ALERT")
    inner class AlertAlert {

        @Test
        fun `devuelve predicción con alertLevel ALERT`() = runTest {
            coEvery { cognitiveAnalyzer.analyze(USER_ID) }  returns buildAnalysisResult(AlertLevel.ALERT)
            coEvery { mlRepository.savePrediction(any()) }  returns Result.Success(buildSavedPrediction(AlertLevel.ALERT))

            val outcome = useCase(USER_ID)

            assertEquals(AlertLevel.ALERT, (outcome as Result.Success).data.alertLevel)
        }

        @Test
        fun `predicción ALERT indica requiresAttention true`() = runTest {
            coEvery { cognitiveAnalyzer.analyze(USER_ID) }  returns buildAnalysisResult(AlertLevel.ALERT)
            val pred = buildSavedPrediction(AlertLevel.ALERT)
            coEvery { mlRepository.savePrediction(any()) }  returns Result.Success(pred)

            val outcome = useCase(USER_ID)

            assertTrue((outcome as Result.Success).data.requiresAttention)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Sin datos suficientes")
    inner class InsufficientData {

        @Test
        fun `con 0 sesiones devuelve Result Error`() = runTest {
            coEvery { sessionRepository.countAllCompletedSessions(USER_ID) } returns
                Result.Success(0)

            val outcome = useCase(USER_ID)

            assertInstanceOf(Result.Error::class.java, outcome)
        }

        @Test
        fun `con sesiones por debajo del mínimo devuelve Result Error`() = runTest {
            coEvery { sessionRepository.countAllCompletedSessions(USER_ID) } returns
                Result.Success(AnalyzeCognitivePatternUseCase.MIN_SESSIONS_REQUIRED - 1)

            val outcome = useCase(USER_ID)

            assertInstanceOf(Result.Error::class.java, outcome)
        }

        @Test
        fun `con sesiones insuficientes NO llama a cognitiveAnalyzer`() = runTest {
            coEvery { sessionRepository.countAllCompletedSessions(USER_ID) } returns
                Result.Success(0)

            useCase(USER_ID)

            coVerify(exactly = 0) { cognitiveAnalyzer.analyze(any()) }
        }

        @Test
        fun `error en countAllCompletedSessions devuelve Result Error`() = runTest {
            coEvery { sessionRepository.countAllCompletedSessions(USER_ID) } returns
                Result.Error(Exception("DB unavailable"))

            val outcome = useCase(USER_ID)

            assertInstanceOf(Result.Error::class.java, outcome)
        }

        @Test
        fun `fallo en savePrediction propaga el error`() = runTest {
            coEvery { cognitiveAnalyzer.analyze(USER_ID) }  returns buildAnalysisResult(AlertLevel.NORMAL)
            coEvery { mlRepository.savePrediction(any()) }  returns Result.Error(Exception("insert failed"))

            val outcome = useCase(USER_ID)

            assertInstanceOf(Result.Error::class.java, outcome)
        }
    }
}
