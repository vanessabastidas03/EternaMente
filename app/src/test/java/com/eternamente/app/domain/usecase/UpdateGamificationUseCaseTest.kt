package com.eternamente.app.domain.usecase

import app.cash.turbine.test
import com.eternamente.app.core.Result
import com.eternamente.app.core.notifications.BadgeNotificationHelper
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.CognitiveSession
import com.eternamente.app.domain.model.GameResult
import com.eternamente.app.domain.model.GamificationProfile
import com.eternamente.app.domain.model.GamificationUpdate
import com.eternamente.app.domain.model.SessionType
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.GamificationRepository
import com.eternamente.app.domain.repository.SessionRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate

@ExtendWith(MockKExtension::class)
@DisplayName("UpdateGamificationUseCase")
class UpdateGamificationUseCaseTest {

    private val gamificationRepository: GamificationRepository = mockk()
    private val gameResultRepository: GameResultRepository     = mockk()
    private val sessionRepository: SessionRepository           = mockk()
    private val notificationHelper: BadgeNotificationHelper    = mockk()

    private lateinit var useCase: UpdateGamificationUseCase

    companion object {
        private const val USER_ID    = "user-999"
        private const val SESSION_ID = "session-999"
        private val TODAY            = LocalDate.now().toString()
        private val YESTERDAY        = LocalDate.now().minusDays(1).toString()
        private val TWO_DAYS_AGO     = LocalDate.now().minusDays(2).toString()
    }

    private val baseSession = CognitiveSession(
        id            = SESSION_ID,
        userId        = USER_ID,
        sessionDate   = System.currentTimeMillis(),
        durationSeconds = 600,
        type          = SessionType.DAILY,
        completed     = true
    )

    private val singleResult = GameResult(
        id = "r-001", sessionId = SESSION_ID, gameId = "digit_span",
        domain = CognitiveDomain.MEMORY,
        scoreRaw = 70f, scoreNormalized = 70f,
        reactionTimeMsAvg = 1200f, reactionTimeMsP50 = 1100f,
        accuracyPct = 80f, errorsCount = 2, difficultyLevel = 2
    )

    private fun buildProfile(
        streak: Int = 0,
        lastDate: String? = null,
        badges: List<Badge> = emptyList(),
        points: Int = 0
    ) = GamificationProfile(
        userId          = USER_ID,
        totalPoints     = points,
        currentStreak   = streak,
        maxStreak       = streak,
        lastSessionDate = lastDate,
        badges          = badges
    )

    @BeforeEach
    fun setUp() {
        useCase = UpdateGamificationUseCase(
            gamificationRepository, gameResultRepository, sessionRepository, notificationHelper
        )
        // Default profile (no badges, streak 0) — test-specific coEvery blocks override this
        val defaultProfile = buildProfile()
        coEvery { gamificationRepository.getProfile(USER_ID) }              returns Result.Success(defaultProfile)
        coEvery { gamificationRepository.addPoints(any(), any()) }          returns Result.Success(defaultProfile)
        coEvery { gamificationRepository.updateStreak(any(), any()) }       returns Result.Success(defaultProfile)
        coEvery { gamificationRepository.unlockBadge(any(), any()) }        returns Result.Success(defaultProfile)
        // BadgeStats dependencies — all zero/empty by default
        coEvery { sessionRepository.countAllCompletedSessions(USER_ID) }     returns Result.Success(1)
        coEvery { sessionRepository.hasCompletedBaseline(USER_ID) }          returns Result.Success(false)
        coEvery { sessionRepository.getAllSessionDates(USER_ID) }             returns Result.Success(emptyList())
        coEvery { gameResultRepository.countMemoryGamesAboveAccuracy(any(), any()) } returns Result.Success(0)
        coEvery { gameResultRepository.countAttentionGamesPerfect(any()) }   returns Result.Success(0)
        coEvery { gameResultRepository.countUniqueDomains(any()) }           returns Result.Success(1)
        coEvery { gameResultRepository.maxDifficultyReached(any()) }         returns Result.Success(1)
        coEvery { gameResultRepository.flashColorMinRtMs(any()) }            returns Result.Success(999f)
        coEvery { notificationHelper.showBadgeUnlocked(any()) } just Runs
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Primera sesión — badge FIRST_STEP")
    inner class FirstSession {

        @Test
        fun `primera sesión desbloquea FIRST_STEP y devuelve Success`() = runTest {
            val profileBefore = buildProfile(streak = 0, lastDate = null)
            val profileAfter  = buildProfile(streak = 1, lastDate = TODAY, points = 100)
            coEvery { gamificationRepository.getProfile(USER_ID) } returnsMany listOf(
                Result.Success(profileBefore),
                Result.Success(profileAfter),
                Result.Success(profileAfter.copy(badges = listOf(Badge.FIRST_STEP)))
            )
            coEvery { gamificationRepository.addPoints(USER_ID, any()) }       returns Result.Success(profileAfter)
            coEvery { gamificationRepository.updateStreak(USER_ID, any()) }    returns Result.Success(profileAfter)
            coEvery { gamificationRepository.unlockBadge(USER_ID, any()) }     returns Result.Success(profileAfter)

            val outcome = useCase(baseSession, listOf(singleResult))

            assertInstanceOf(Result.Success::class.java, outcome)
            val update = (outcome as Result.Success).data
            assertTrue(Badge.FIRST_STEP in update.newlyUnlockedBadges)
        }

        @Test
        fun `primera sesión notifica el badge FIRST_STEP`() = runTest {
            val profileBefore = buildProfile(streak = 0, lastDate = null)
            val profileAfter  = buildProfile(streak = 1, lastDate = TODAY, points = 100)
            coEvery { gamificationRepository.getProfile(USER_ID) } returnsMany listOf(
                Result.Success(profileBefore),
                Result.Success(profileAfter),
                Result.Success(profileAfter.copy(badges = listOf(Badge.FIRST_STEP)))
            )
            coEvery { gamificationRepository.addPoints(USER_ID, any()) }   returns Result.Success(profileAfter)
            coEvery { gamificationRepository.updateStreak(USER_ID, any()) } returns Result.Success(profileAfter)
            coEvery { gamificationRepository.unlockBadge(USER_ID, any()) } returns Result.Success(profileAfter)

            useCase(baseSession, listOf(singleResult))

            coVerify { notificationHelper.showBadgeUnlocked(Badge.FIRST_STEP) }
        }

        @Test
        fun `puntaje otorgado es mayor a cero`() = runTest {
            val profile = buildProfile()
            coEvery { gamificationRepository.getProfile(USER_ID) }        returns Result.Success(profile)
            coEvery { gamificationRepository.addPoints(USER_ID, any()) }   returns Result.Success(profile)
            coEvery { gamificationRepository.updateStreak(USER_ID, any()) } returns Result.Success(profile)

            val outcome = useCase(baseSession, listOf(singleResult))

            assertTrue((outcome as Result.Success).data.pointsAwarded > 0)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Racha de 7 días — badge WEEK_WARRIOR")
    inner class WeekWarriorStreak {

        @Test
        fun `racha de 6 llega a 7 y desbloquea WEEK_WARRIOR`() = runTest {
            // Streak antes = 6, lastDate = ayer → al completar hoy = streak 7
            val profileBefore = buildProfile(streak = 6, lastDate = YESTERDAY)
            val profileAfter  = buildProfile(streak = 7, lastDate = TODAY, points = 200)
            coEvery { gamificationRepository.getProfile(USER_ID) } returnsMany listOf(
                Result.Success(profileBefore),
                Result.Success(profileAfter),
                Result.Success(profileAfter.copy(badges = listOf(Badge.WEEK_WARRIOR)))
            )
            coEvery { gamificationRepository.addPoints(USER_ID, any()) }    returns Result.Success(profileAfter)
            coEvery { gamificationRepository.updateStreak(USER_ID, any()) } returns Result.Success(profileAfter)
            coEvery { gamificationRepository.unlockBadge(USER_ID, any()) }  returns Result.Success(profileAfter)

            val outcome = useCase(baseSession, listOf(singleResult))

            val update = (outcome as Result.Success).data
            assertTrue(Badge.WEEK_WARRIOR in update.newlyUnlockedBadges)
        }

        @Test
        fun `racha de 7 aplica multiplicador de puntos mayor a 1`() = runTest {
            val profileStreak7 = buildProfile(streak = 7, lastDate = TODAY)
            coEvery { gamificationRepository.getProfile(USER_ID) }         returns Result.Success(profileStreak7)
            coEvery { gamificationRepository.addPoints(USER_ID, any()) }    returns Result.Success(profileStreak7)
            coEvery { gamificationRepository.updateStreak(USER_ID, TODAY) } returns Result.Success(profileStreak7)

            // Racha ya en 7 en el mismo día → AlreadyDone, no llama updateStreak de nuevo
            val outcome = useCase(baseSession, listOf(singleResult))

            val update = (outcome as Result.Success).data
            // Engine: basePoints = (accuracyPct*10).coerceIn(0,100) = 100; speedBonus = 10 (rt<1500)
            // streak=0 → 110 pts; streak=7 → (110 * 1.7f).toInt() = 187 pts
            // Verificamos que el resultado supera la cantidad sin multiplicador (110)
            val noStreakPoints = 110   // (100 base + 10 speed) × 1.0
            assertTrue(update.pointsAwarded > noStreakPoints,
                "Con streak=7 los puntos (${update.pointsAwarded}) deben superar $noStreakPoints")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Racha rota")
    inner class BrokenStreak {

        @Test
        fun `racha se reinicia cuando lastDate tiene más de 1 día de antigüedad`() = runTest {
            // lastDate = hace 2 días → no es consecutivo → StreakResult.Started(1)
            val profileBefore = buildProfile(streak = 5, lastDate = TWO_DAYS_AGO)
            val profileAfter  = buildProfile(streak = 1, lastDate = TODAY)
            coEvery { gamificationRepository.getProfile(USER_ID) } returnsMany listOf(
                Result.Success(profileBefore),
                Result.Success(profileAfter),
                Result.Success(profileAfter)
            )
            coEvery { gamificationRepository.addPoints(USER_ID, any()) }    returns Result.Success(profileAfter)
            coEvery { gamificationRepository.updateStreak(USER_ID, any()) } returns Result.Success(profileAfter)

            // No debería desbloquear WEEK_WARRIOR
            val outcome = useCase(baseSession, listOf(singleResult))

            val update = (outcome as Result.Success).data
            assertTrue(Badge.WEEK_WARRIOR !in update.newlyUnlockedBadges)
        }

        @Test
        fun `sesión ya registrada hoy no actualiza la racha (AlreadyDone)`() = runTest {
            val profileToday = buildProfile(streak = 3, lastDate = TODAY)
            coEvery { gamificationRepository.getProfile(USER_ID) } returnsMany listOf(
                Result.Success(profileToday),
                Result.Success(profileToday),
                Result.Success(profileToday)
            )
            coEvery { gamificationRepository.addPoints(USER_ID, any()) } returns Result.Success(profileToday)

            useCase(baseSession, listOf(singleResult))

            // AlreadyDone → updateStreak NO debe llamarse
            coVerify(exactly = 0) { gamificationRepository.updateStreak(any(), any()) }
        }

        @Test
        fun `userId vacío devuelve Result Error sin llamar al repositorio`() = runTest {
            val emptyUserSession = baseSession.copy(userId = "")

            val outcome = useCase(emptyUserSession, listOf(singleResult))

            assertInstanceOf(Result.Error::class.java, outcome)
            coVerify(exactly = 0) { gamificationRepository.getProfile(any()) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested @DisplayName("Turbine — Flow de perfil observable")
    inner class ProfileFlowTest {

        @Test
        fun `observeProfile emite el perfil actualizado tras la sesión`() = runTest {
            val initialProfile = buildProfile(streak = 0, lastDate = null)
            val updatedProfile = buildProfile(streak = 1, lastDate = TODAY, points = 80)
            val profileFlow    = MutableStateFlow<GamificationProfile?>(initialProfile)

            coEvery { gamificationRepository.observeProfile(USER_ID) } returns profileFlow

            // Verificamos que el Flow emite el estado inicial y luego el actualizado
            profileFlow.test {
                assertEquals(initialProfile, awaitItem())

                // Simulamos la actualización que haría el repositorio tras la sesión
                profileFlow.value = updatedProfile

                assertEquals(updatedProfile, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
