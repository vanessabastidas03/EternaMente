package com.eternamente.app.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eternamente.app.data.local.database.EternaDatabase
import com.eternamente.app.data.local.database.dao.BaselineDao
import com.eternamente.app.data.local.database.dao.GameResultDao
import com.eternamente.app.data.local.database.dao.GamificationDao
import com.eternamente.app.data.local.database.dao.MlPredictionDao
import com.eternamente.app.data.local.database.dao.SessionDao
import com.eternamente.app.data.local.database.dao.UserDao
import com.eternamente.app.data.local.database.entity.BaselineEntity
import com.eternamente.app.data.local.database.entity.GameResultEntity
import com.eternamente.app.data.local.database.entity.GamificationEntity
import com.eternamente.app.data.local.database.entity.MlPredictionEntity
import com.eternamente.app.data.local.database.entity.SessionEntity
import com.eternamente.app.data.local.database.entity.UserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de integración para [EternaDatabase] usando una base de datos en memoria.
 *
 * **Sin SQLCipher:** [EternaDatabase.createInMemory] omite [SupportFactory] para
 * que las librerías nativas de SQLCipher no sean necesarias en el runner de tests.
 *
 * **Sin persistencia entre tests:** cada `@Before` crea una BD nueva y `@After` la cierra.
 *
 * Cubre:
 * - CRUD básico de todas las entidades
 * - Cascada de eliminación por FK (eliminar user → cascada a sessions, results, etc.)
 * - Queries de rango de fechas en [SessionDao]
 * - Queries agrupadas en [GameResultDao]
 * - Operaciones atómicas de streak en [GamificationDao]
 * - updateByDomain en [BaselineDao]
 * - Flow reactivo (primer valor esperado)
 */
@RunWith(AndroidJUnit4::class)
class EternaDatabaseTest {

    private lateinit var db: EternaDatabase
    private lateinit var userDao: UserDao
    private lateinit var sessionDao: SessionDao
    private lateinit var gameResultDao: GameResultDao
    private lateinit var baselineDao: BaselineDao
    private lateinit var mlDao: MlPredictionDao
    private lateinit var gamificationDao: GamificationDao

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val user1 = UserEntity(
        id = "u1", email = "ana@test.com", name = "Ana",
        age = 72, educationYears = 12, gender = "Mujer",
        createdAt = 1_000_000L, consentGivenAt = null
    )

    private val session1 = SessionEntity(
        id = "s1", userId = "u1", sessionDate = 1_700_000_000_000L,
        durationSeconds = 600, type = "DAILY", completed = true
    )

    private val result1 = GameResultEntity(
        id = "r1", sessionId = "s1", userId = "u1", gameId = "stroop",
        domain = "ATTENTION", scoreRaw = 75f, scoreNormalized = 80f,
        reactionTimeMsAvg = 450f, reactionTimeMsP50 = 440f,
        accuracyPct = 85f, errorsCount = 3, difficultyLevel = 4
    )

    private val result2 = GameResultEntity(
        id = "r2", sessionId = "s1", userId = "u1", gameId = "digit_span",
        domain = "MEMORY", scoreRaw = 60f, scoreNormalized = 65f,
        reactionTimeMsAvg = 500f, reactionTimeMsP50 = 490f,
        accuracyPct = 70f, errorsCount = 6, difficultyLevel = 3
    )

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db              = EternaDatabase.createInMemory(context)
        userDao         = db.userDao()
        sessionDao      = db.sessionDao()
        gameResultDao   = db.gameResultDao()
        baselineDao     = db.baselineDao()
        mlDao           = db.mlPredictionDao()
        gamificationDao = db.gamificationDao()
    }

    @After
    fun closeDb() = db.close()

    // ── UserDao ───────────────────────────────────────────────────────────────

    @Test
    fun userDao_insertAndGetById() = runTest {
        userDao.insert(user1)
        val loaded = userDao.getById("u1")
        assertNotNull(loaded)
        assertEquals("ana@test.com", loaded!!.email)
    }

    @Test
    fun userDao_getByEmail_returnsCorrectUser() = runTest {
        userDao.insert(user1)
        val loaded = userDao.getByEmail("ana@test.com")
        assertNotNull(loaded)
        assertEquals("u1", loaded!!.id)
    }

    @Test
    fun userDao_getByEmail_returnsNull_forUnknownEmail() = runTest {
        val loaded = userDao.getByEmail("noexiste@test.com")
        assertNull(loaded)
    }

    @Test
    fun userDao_update_modifiesExistingRecord() = runTest {
        userDao.insert(user1)
        userDao.update(user1.copy(age = 75))
        val updated = userDao.getById("u1")
        assertEquals(75, updated!!.age)
    }

    @Test
    fun userDao_deleteById_removesRecord() = runTest {
        userDao.insert(user1)
        userDao.deleteById("u1")
        assertNull(userDao.getById("u1"))
    }

    @Test
    fun userDao_observe_emitsUpdates() = runTest {
        userDao.insert(user1)
        val observed = userDao.observe("u1").first()
        assertNotNull(observed)
        assertEquals("u1", observed!!.id)
    }

    // ── SessionDao ────────────────────────────────────────────────────────────

    @Test
    fun sessionDao_insertAndGetLastSession() = runTest {
        userDao.insert(user1)
        sessionDao.insert(session1)
        val last = sessionDao.getLastSession("u1")
        assertNotNull(last)
        assertEquals("s1", last!!.id)
    }

    @Test
    fun sessionDao_getSessionCount_onlyCountsCompleted() = runTest {
        userDao.insert(user1)
        sessionDao.insert(session1) // completed = true
        sessionDao.insert(session1.copy(id = "s2", completed = false))
        val count = sessionDao.getSessionCount("u1")
        assertEquals(1, count)
    }

    @Test
    fun sessionDao_getSessionsForUser_dateRangeFilter() = runTest {
        userDao.insert(user1)
        sessionDao.insert(session1) // sessionDate = 1_700_000_000_000L

        val inRange  = sessionDao.getSessionsForUser("u1", 1_699_000_000_000L, 1_701_000_000_000L).first()
        val outRange = sessionDao.getSessionsForUser("u1", 1_000_000_000_000L, 1_100_000_000_000L).first()

        assertEquals(1, inRange.size)
        assertEquals(0, outRange.size)
    }

    @Test
    fun sessionDao_markCompleted_setsFlag() = runTest {
        userDao.insert(user1)
        sessionDao.insert(session1.copy(completed = false, durationSeconds = null))
        sessionDao.markCompleted("s1", 720)
        val updated = sessionDao.getById("s1")
        assertTrue(updated!!.completed)
        assertEquals(720, updated.durationSeconds)
    }

    @Test
    fun sessionDao_cascadeDelete_removesSessionsWhenUserDeleted() = runTest {
        userDao.insert(user1)
        sessionDao.insert(session1)
        userDao.deleteById("u1")
        assertNull(sessionDao.getById("s1"))
    }

    // ── GameResultDao ─────────────────────────────────────────────────────────

    @Test
    fun gameResultDao_insertAllAndGetBySession() = runTest {
        userDao.insert(user1)
        sessionDao.insert(session1)
        gameResultDao.insertAll(listOf(result1, result2))

        val results = gameResultDao.getResultsBySession("s1").first()
        assertEquals(2, results.size)
    }

    @Test
    fun gameResultDao_getResultsByDomain_filtersCorrectly() = runTest {
        userDao.insert(user1)
        sessionDao.insert(session1)
        gameResultDao.insertAll(listOf(result1, result2))

        val attention = gameResultDao.getResultsByDomain("u1", "ATTENTION", 10)
        val memory    = gameResultDao.getResultsByDomain("u1", "MEMORY", 10)

        assertEquals(1, attention.size)
        assertEquals("r1", attention.first().id)
        assertEquals(1, memory.size)
        assertEquals("r2", memory.first().id)
    }

    @Test
    fun gameResultDao_getAveragesByDomain_computesCorrectly() = runTest {
        userDao.insert(user1)
        sessionDao.insert(session1)
        gameResultDao.insertAll(listOf(result1, result2))

        val avgs = gameResultDao.getAveragesByDomainSince("u1", 0L)
        val attentionAvg = avgs.firstOrNull { it.domain == "ATTENTION" }
        assertNotNull(attentionAvg)
        assertEquals(80f, attentionAvg!!.avgScore, 0.01f)
    }

    @Test
    fun gameResultDao_cascadeDelete_removesResultsWithSession() = runTest {
        userDao.insert(user1)
        sessionDao.insert(session1)
        gameResultDao.insert(result1)

        sessionDao.update(session1.copy()) // dummy update
        userDao.deleteById("u1") // cascade → session → result

        val results = gameResultDao.getResultsBySession("s1").first()
        assertTrue(results.isEmpty())
    }

    // ── BaselineDao ───────────────────────────────────────────────────────────

    @Test
    fun baselineDao_insertAndGetByUserId() = runTest {
        userDao.insert(user1)
        val baseline = BaselineEntity(
            userId = "u1", memoryScore = 70f, attentionScore = 75f,
            executiveScore = 68f, languageScore = 80f, orientationScore = 85f,
            overallScore = 75.6f, calculatedAt = 1_700_000_000_000L
        )
        baselineDao.insert(baseline)

        val loaded = baselineDao.getByUserId("u1")
        assertNotNull(loaded)
        assertEquals(70f, loaded!!.memoryScore, 0.01f)
    }

    @Test
    fun baselineDao_updateByDomain_updatesOnlyTargetDomain() = runTest {
        userDao.insert(user1)
        val baseline = BaselineEntity(
            userId = "u1", memoryScore = 70f, attentionScore = 75f,
            executiveScore = 68f, languageScore = 80f, orientationScore = 85f,
            overallScore = 75.6f, calculatedAt = System.currentTimeMillis()
        )
        baselineDao.insert(baseline)
        baselineDao.updateByDomain("u1", "MEMORY", 90f)

        val updated = baselineDao.getByUserId("u1")!!
        assertEquals(90f, updated.memoryScore, 0.01f)
        assertEquals(75f, updated.attentionScore, 0.01f)   // no cambió
        // overallScore recalculado = (90+75+68+80+85)/5 = 79.6
        assertEquals(79.6f, updated.overallScore, 0.01f)
    }

    // ── GamificationDao ───────────────────────────────────────────────────────

    @Test
    fun gamificationDao_insertAndGetByUserId() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", totalPoints = 100))

        val loaded = gamificationDao.getByUserId("u1")
        assertNotNull(loaded)
        assertEquals(100, loaded!!.totalPoints)
    }

    @Test
    fun gamificationDao_addPoints_accumulates() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", totalPoints = 50))
        gamificationDao.addPoints("u1", 25)

        val updated = gamificationDao.getByUserId("u1")!!
        assertEquals(75, updated.totalPoints)
    }

    @Test
    fun gamificationDao_incrementStreak_updatesCurrentAndMax() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", currentStreak = 4, maxStreak = 5))
        gamificationDao.incrementStreak("u1", "2024-03-15")

        val updated = gamificationDao.getByUserId("u1")!!
        assertEquals(5, updated.currentStreak)
        assertEquals(5, updated.maxStreak)   // max no cambia porque 5 == 5
    }

    @Test
    fun gamificationDao_incrementStreak_updatesMaxWhenExceeded() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", currentStreak = 5, maxStreak = 5))
        gamificationDao.incrementStreak("u1", "2024-03-15")

        val updated = gamificationDao.getByUserId("u1")!!
        assertEquals(6, updated.currentStreak)
        assertEquals(6, updated.maxStreak)   // nuevo máximo histórico
    }

    @Test
    fun gamificationDao_resetStreak_setsCurrentToZero() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", currentStreak = 10, maxStreak = 10))
        gamificationDao.resetStreak("u1")

        val updated = gamificationDao.getByUserId("u1")!!
        assertEquals(0, updated.currentStreak)
        assertEquals(10, updated.maxStreak)   // maxStreak se preserva
    }

    @Test
    fun gamificationDao_appendBadge_buildsList() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", badges = ""))
        gamificationDao.appendBadge("u1", "FIRST_STEP")
        gamificationDao.appendBadge("u1", "WEEK_WARRIOR")

        val badges = gamificationDao.getByUserId("u1")!!.badges
        assertTrue(badges.contains("FIRST_STEP"))
        assertTrue(badges.contains("WEEK_WARRIOR"))
    }

    // ── MlPredictionDao ────────────────────────────────────────────────────────

    @Test
    fun mlPredictionDao_insertAndGetLatest() = runTest {
        userDao.insert(user1)
        val pred = MlPredictionEntity(
            id = "p1", userId = "u1", predictionDate = 1_700_000_000_000L,
            riskScore = 0.3f, alertLevel = "NORMAL",
            domainsFlagged = "", explanation = "Sin riesgo"
        )
        mlDao.insert(pred)

        val latest = mlDao.getLatestForUser("u1")
        assertNotNull(latest)
        assertEquals("p1", latest!!.id)
    }

    @Test
    fun mlPredictionDao_getHistory_returnsInOrder() = runTest {
        userDao.insert(user1)
        mlDao.insert(MlPredictionEntity("p1", "u1", 1_000L, 0.2f, "NORMAL", "", ""))
        mlDao.insert(MlPredictionEntity("p2", "u1", 2_000L, 0.4f, "WATCH",  "", ""))

        val history = mlDao.getHistoryForUser("u1", 10)
        assertEquals("p2", history.first().id)   // más reciente primero
    }

    // ── Flow reactivo ─────────────────────────────────────────────────────────

    @Test
    fun gamificationDao_observe_emitsOnInsert() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", totalPoints = 200))

        val observed = gamificationDao.observe("u1").first()
        assertNotNull(observed)
        assertEquals(200, observed!!.totalPoints)
    }
}
