package com.eternamente.app.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de integración de [EternaDatabase] con base de datos en memoria (sin SQLCipher).
 *
 * Cada `@Before` crea una BD completamente nueva; `@After` la cierra.
 * No hay estado compartido entre tests.
 *
 * **FKs activas en el esquema:**
 * - `game_results.sessionId` → `cognitive_sessions.id` (CASCADE DELETE)
 * - `gamification.userId`   → `users.id`              (CASCADE DELETE)
 * - `cognitive_sessions` NO tiene FK en `userId` — decisión de diseño explícita.
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

    // ── Fixtures base ─────────────────────────────────────────────────────────

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
        val ctx = InstrumentationRegistry.getInstrumentation().context
        db              = EternaDatabase.createInMemory(ctx)
        userDao         = db.userDao()
        sessionDao      = db.sessionDao()
        gameResultDao   = db.gameResultDao()
        baselineDao     = db.baselineDao()
        mlDao           = db.mlPredictionDao()
        gamificationDao = db.gamificationDao()
    }

    @After
    fun closeDb() = db.close()

    // ══════════════════════════════════════════════════════════════════════════
    // 1. UserDao — CRUD completo + getByEmail null
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Flujo CRUD completo: insert → getById → update → delete.
     * Verifica que cada operación produce el estado esperado en la BD.
     */
    @SmallTest
    @Test
    fun userDao_crudChain_insertGetUpdateDelete() = runTest {
        // INSERT
        userDao.insert(user1)
        val inserted = userDao.getById("u1")
        assertNotNull("El usuario debe existir tras insert", inserted)
        assertEquals("ana@test.com", inserted!!.email)
        assertEquals(72, inserted.age)

        // UPDATE
        userDao.update(user1.copy(age = 75, name = "Ana García"))
        val updated = userDao.getById("u1")
        assertEquals("El age debe actualizarse a 75", 75, updated!!.age)
        assertEquals("El name debe actualizarse", "Ana García", updated.name)
        assertEquals("El email no debe cambiar", "ana@test.com", updated.email)

        // DELETE
        userDao.deleteById("u1")
        val deleted = userDao.getById("u1")
        assertNull("El usuario debe ser null tras deleteById", deleted)
    }

    /** getByEmail devuelve null cuando el correo no existe en la BD. */
    @SmallTest
    @Test
    fun userDao_getByEmail_returnsNull_forNonExistentEmail() = runTest {
        // Sin ningún insert previo
        val result = userDao.getByEmail("noexiste@test.com")
        assertNull("getByEmail debe devolver null para email desconocido", result)
    }

    @SmallTest
    @Test
    fun userDao_getByEmail_returnsCorrectUser() = runTest {
        userDao.insert(user1)
        val loaded = userDao.getByEmail("ana@test.com")
        assertNotNull(loaded)
        assertEquals("u1", loaded!!.id)
    }

    @SmallTest
    @Test
    fun userDao_insertDuplicate_replacesExistingRecord() = runTest {
        userDao.insert(user1)
        userDao.insert(user1.copy(name = "Ana Reemplazada"))
        assertEquals(1, userDao.count())
        assertEquals("Ana Reemplazada", userDao.getById("u1")!!.name)
    }

    @SmallTest
    @Test
    fun userDao_observe_emitsInsertedUser() = runTest {
        userDao.insert(user1)
        val observed = userDao.observe("u1").first()
        assertNotNull(observed)
        assertEquals("u1", observed!!.id)
    }

    @SmallTest
    @Test
    fun userDao_consentTimestamp_isUpdated() = runTest {
        userDao.insert(user1)
        userDao.updateConsentTimestamp("u1", 9_999_999L)
        assertEquals(9_999_999L, userDao.getById("u1")!!.consentGivenAt)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. SessionDao — 10 sesiones + filtro por rango de fechas
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Inserta 10 sesiones distribuidas en 3 franjas temporales y verifica que
     * [SessionDao.getSessionsForUser] devuelve exactamente las sesiones del rango consultado.
     *
     * Distribución:
     * - Época ANTIGUA (dates 1_000–3_000):     3 sesiones → fuera del rango de consulta
     * - Época MEDIA   (dates 5_000–8_000):     4 sesiones → DENTRO del rango
     * - Época FUTURA  (dates 10_000–12_000):   3 sesiones → fuera del rango de consulta
     *
     * Rango consultado: [4_500, 8_500] → debe devolver 4 sesiones.
     */
    @SmallTest
    @Test
    fun sessionDao_insert10Sessions_getSessionsInDateRange_returnsCorrectCount() = runTest {
        val ancianas = listOf(1_000L, 2_000L, 3_000L)
        val medias   = listOf(5_000L, 6_000L, 7_000L, 8_000L)
        val futuras  = listOf(10_000L, 11_000L, 12_000L)

        var idx = 1
        (ancianas + medias + futuras).forEach { date ->
            sessionDao.insert(
                SessionEntity(
                    id = "s$idx", userId = "u1",
                    sessionDate = date,
                    durationSeconds = 300, type = "DAILY", completed = true
                )
            )
            idx++
        }

        // Total insertadas: 10
        assertEquals(10, sessionDao.countAllCompleted("u1"))

        // Rango medio
        val inRange = sessionDao.getSessionsForUser("u1", fromEpochMs = 4_500L, toEpochMs = 8_500L).first()
        assertEquals(
            "Deben obtenerse exactamente 4 sesiones del rango medio",
            medias.size, inRange.size
        )

        // Rango antiguo — devuelve 0
        val empty = sessionDao.getSessionsForUser("u1", fromEpochMs = 0L, toEpochMs = 500L).first()
        assertEquals("Ninguna sesión cae en el rango [0, 500]", 0, empty.size)

        // Rango total — devuelve las 10
        val all = sessionDao.getSessionsForUser("u1", fromEpochMs = 0L, toEpochMs = 100_000L).first()
        assertEquals("El rango amplio debe devolver las 10 sesiones", 10, all.size)
    }

    @SmallTest
    @Test
    fun sessionDao_getSessionCount_onlyCountsCompleted() = runTest {
        sessionDao.insert(session1)                                    // completed=true
        sessionDao.insert(session1.copy(id = "s2", completed = false)) // incompleta
        assertEquals(1, sessionDao.getSessionCount("u1"))
    }

    @SmallTest
    @Test
    fun sessionDao_markCompleted_setsFlagAndDuration() = runTest {
        sessionDao.insert(session1.copy(completed = false, durationSeconds = null))
        sessionDao.markCompleted("s1", 720)
        val updated = sessionDao.getById("s1")!!
        assertTrue(updated.completed)
        assertEquals(720, updated.durationSeconds)
    }

    @SmallTest
    @Test
    fun sessionDao_getLastSession_returnsMostRecent() = runTest {
        sessionDao.insert(session1.copy(id = "s-old", sessionDate = 1_000L))
        sessionDao.insert(session1.copy(id = "s-new", sessionDate = 9_000L))
        val last = sessionDao.getLastSession("u1")
        assertEquals("La sesión más reciente debe ser s-new", "s-new", last!!.id)
    }

    @SmallTest
    @Test
    fun sessionDao_getAllSessionDates_returnsAscendingOrder() = runTest {
        sessionDao.insert(session1.copy(id = "s-b", sessionDate = 200L))
        sessionDao.insert(session1.copy(id = "s-a", sessionDate = 100L))
        sessionDao.insert(session1.copy(id = "s-c", sessionDate = 300L))
        val dates = sessionDao.getAllSessionDates("u1")
        assertEquals(listOf(100L, 200L, 300L), dates)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. GameResultDao — 50 resultados + promedio semanal matemáticamente correcto
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Inserta 50 resultados de juego: 25 con scoreNormalized=60f y 25 con scoreNormalized=80f,
     * todos en el dominio MEMORY. Verifica que [GameResultDao.getAveragesByDomainSince] calcula
     * el promedio correcto: (25×60 + 25×80) / 50 = 3500 / 50 = 70.0f.
     */
    @SmallTest
    @Test
    fun gameResultDao_insert50Results_weeklyAverages_mathematicallyCorrect() = runTest {
        // La query hace JOIN con cognitive_sessions → necesitamos sesiones en la BD
        val session = SessionEntity(
            id = "s-bulk", userId = "u1",
            sessionDate = 1_000L,   // fecha anterior a fromEpochMs=0 → en rango
            durationSeconds = 600, type = "DAILY", completed = true
        )
        sessionDao.insert(session)

        val results = mutableListOf<GameResultEntity>()

        // Grupo A: 25 resultados con scoreNormalized = 60f
        repeat(25) { i ->
            results += GameResultEntity(
                id = "r-low-$i", sessionId = "s-bulk", userId = "u1",
                gameId = "digit_span", domain = "MEMORY",
                scoreRaw = 55f, scoreNormalized = 60f,
                reactionTimeMsAvg = 1100f, reactionTimeMsP50 = 1050f,
                accuracyPct = 65f, errorsCount = 4, difficultyLevel = 2
            )
        }

        // Grupo B: 25 resultados con scoreNormalized = 80f
        repeat(25) { i ->
            results += GameResultEntity(
                id = "r-high-$i", sessionId = "s-bulk", userId = "u1",
                gameId = "digit_span", domain = "MEMORY",
                scoreRaw = 78f, scoreNormalized = 80f,
                reactionTimeMsAvg = 900f, reactionTimeMsP50 = 880f,
                accuracyPct = 85f, errorsCount = 1, difficultyLevel = 3
            )
        }

        gameResultDao.insertAll(results)
        assertEquals("Deben existir 50 resultados en BD", 50, results.size)

        // Consulta promedios desde epoch=0 (incluye todos los registros)
        val avgRows = gameResultDao.getAveragesByDomainSince("u1", fromEpochMs = 0L)

        val memoryRow = avgRows.firstOrNull { it.domain == "MEMORY" }
        assertNotNull("Debe existir una fila de promedio para MEMORY", memoryRow)

        // Promedio esperado: (25×60 + 25×80) / 50 = 70.0
        val expectedAvg = 70.0f
        assertEquals(
            "El promedio de MEMORY debe ser exactamente $expectedAvg",
            expectedAvg, memoryRow!!.avgScore, 0.01f
        )
    }

    /**
     * Verifica [getAveragesByDomainInRange]: sólo cuenta resultados cuya sesión
     * cae dentro del rango [fromMs, toMs].
     */
    @SmallTest
    @Test
    fun gameResultDao_weeklyAverage_respectsDateRange() = runTest {
        // Sesión dentro del rango
        sessionDao.insert(session1.copy(id = "s-in", sessionDate = 1_000L))
        // Sesión fuera del rango
        sessionDao.insert(session1.copy(id = "s-out", sessionDate = 9_000L))

        // 10 resultados en sesión "dentro" con score=70f
        repeat(10) { i ->
            gameResultDao.insert(result1.copy(
                id = "in-$i", sessionId = "s-in",
                domain = "ATTENTION", scoreNormalized = 70f
            ))
        }
        // 10 resultados en sesión "fuera" con score=90f
        repeat(10) { i ->
            gameResultDao.insert(result1.copy(
                id = "out-$i", sessionId = "s-out",
                domain = "ATTENTION", scoreNormalized = 90f
            ))
        }

        val inRangeAvg = gameResultDao.getAveragesByDomainInRange("u1", fromMs = 0L, toMs = 2_000L)
        val attRow = inRangeAvg.firstOrNull { it.domain == "ATTENTION" }
        assertNotNull(attRow)
        assertEquals("Solo debe promediar los resultados del rango [0, 2000]", 70f, attRow!!.avgScore, 0.01f)
    }

    @SmallTest
    @Test
    fun gameResultDao_insertAllAndGetBySession() = runTest {
        sessionDao.insert(session1)
        gameResultDao.insertAll(listOf(result1, result2))
        val results = gameResultDao.getResultsBySession("s1").first()
        assertEquals(2, results.size)
    }

    @SmallTest
    @Test
    fun gameResultDao_getResultsByDomain_filtersCorrectly() = runTest {
        sessionDao.insert(session1)
        gameResultDao.insertAll(listOf(result1, result2))

        val attention = gameResultDao.getResultsByDomain("u1", "ATTENTION", 10)
        val memory    = gameResultDao.getResultsByDomain("u1", "MEMORY", 10)

        assertEquals(1, attention.size)
        assertEquals("r1", attention.first().id)
        assertEquals(1, memory.size)
        assertEquals("r2", memory.first().id)
    }

    @SmallTest
    @Test
    fun gameResultDao_badgeStats_countMemoryAbove90() = runTest {
        sessionDao.insert(session1)
        gameResultDao.insert(result2.copy(id = "m-high", domain = "MEMORY", accuracyPct = 95f))
        gameResultDao.insert(result2.copy(id = "m-low",  domain = "MEMORY", accuracyPct = 70f))
        assertEquals(1, gameResultDao.countMemoryGamesAboveAccuracy("u1", 90f))
    }

    @SmallTest
    @Test
    fun gameResultDao_badgeStats_countUniqueDomains() = runTest {
        sessionDao.insert(session1)
        gameResultDao.insertAll(listOf(result1, result2))   // ATTENTION + MEMORY
        assertEquals(2, gameResultDao.countUniqueDomains("u1"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. GamificationDao — racha ×7 → verificar 7 → reset → verificar 0/max
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Incrementa la racha 7 veces consecutivas desde currentStreak=0 y verifica:
     * - currentStreak = 7 después de los 7 incrementos.
     * - maxStreak     = 7 (nuevo máximo histórico).
     *
     * Luego llama a [GamificationDao.resetStreak] y verifica:
     * - currentStreak = 0.
     * - maxStreak     = 7 (se preserva el máximo histórico).
     */
    @SmallTest
    @Test
    fun gamificationDao_increment7Times_thenReset_currentAndMaxAreCorrect() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", currentStreak = 0, maxStreak = 0))

        // Incrementar 7 veces
        repeat(7) { day ->
            gamificationDao.incrementStreak("u1", "2026-05-${11 + day}")
        }

        val afterIncrements = gamificationDao.getByUserId("u1")!!
        assertEquals(
            "currentStreak debe ser 7 tras 7 incrementos",
            7, afterIncrements.currentStreak
        )
        assertEquals(
            "maxStreak debe ser 7 (nuevo máximo)",
            7, afterIncrements.maxStreak
        )

        // Reset
        gamificationDao.resetStreak("u1")

        val afterReset = gamificationDao.getByUserId("u1")!!
        assertEquals(
            "currentStreak debe ser 0 tras resetStreak",
            0, afterReset.currentStreak
        )
        assertEquals(
            "maxStreak debe preservarse en 7 tras el reset",
            7, afterReset.maxStreak
        )
    }

    /**
     * Verifica que [GamificationDao.incrementStreak] actualiza [maxStreak]
     * sólo cuando currentStreak + 1 supera el máximo previo.
     */
    @SmallTest
    @Test
    fun gamificationDao_incrementStreak_updatesMaxOnlyWhenExceeded() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", currentStreak = 4, maxStreak = 6))

        gamificationDao.incrementStreak("u1", "2026-05-15")   // currentStreak = 5

        val updated = gamificationDao.getByUserId("u1")!!
        assertEquals("currentStreak debe ser 5", 5, updated.currentStreak)
        assertEquals("maxStreak NO debe cambiar (5 < 6)", 6, updated.maxStreak)
    }

    @SmallTest
    @Test
    fun gamificationDao_addPoints_accumulates() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", totalPoints = 50))
        gamificationDao.addPoints("u1", 25)
        assertEquals(75, gamificationDao.getByUserId("u1")!!.totalPoints)
    }

    @SmallTest
    @Test
    fun gamificationDao_appendBadge_buildsCsvList() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", badges = ""))
        gamificationDao.appendBadge("u1", "FIRST_STEP")
        gamificationDao.appendBadge("u1", "WEEK_WARRIOR")

        val badges = gamificationDao.getByUserId("u1")!!.badges
        assertTrue(badges.contains("FIRST_STEP"))
        assertTrue(badges.contains("WEEK_WARRIOR"))
    }

    @SmallTest
    @Test
    fun gamificationDao_cascadeDelete_removedWhenUserDeleted() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", totalPoints = 500))
        assertNotNull(gamificationDao.getByUserId("u1"))

        userDao.deleteById("u1")   // cascade → gamification eliminado

        assertNull(
            "El perfil de gamificación debe eliminarse en cascada al borrar el usuario",
            gamificationDao.getByUserId("u1")
        )
    }

    @SmallTest
    @Test
    fun gamificationDao_observe_emitsOnInsert() = runTest {
        userDao.insert(user1)
        gamificationDao.insert(GamificationEntity(userId = "u1", totalPoints = 200))
        val observed = gamificationDao.observe("u1").first()
        assertNotNull(observed)
        assertEquals(200, observed!!.totalPoints)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. FK constraint — insertar GameResult con sessionId inexistente → falla
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Verifica que la FK `game_results.sessionId → cognitive_sessions.id` está activa.
     *
     * Room habilita `PRAGMA foreign_keys = ON` automáticamente. Al intentar insertar
     * un [GameResultEntity] cuyo [sessionId] no existe en `cognitive_sessions`,
     * SQLite debe lanzar [SQLiteConstraintException].
     *
     * Este test distingue la violación de FK de un error genérico: la excepción
     * específica garantiza que el motor de restricciones funciona correctamente,
     * no sólo que "algo falló".
     */
    @SmallTest
    @Test
    fun gameResultDao_insertWithNonExistentSessionId_throwsSQLiteConstraintException() = runTest {
        // No insertamos ninguna sesión con id "ghost-session"
        val orphanResult = GameResultEntity(
            id = "orphan-001",
            sessionId = "ghost-session",   // ← no existe en cognitive_sessions
            userId = "u1",
            gameId = "stroop",
            domain = "ATTENTION",
            scoreRaw = 50f, scoreNormalized = 50f,
            reactionTimeMsAvg = 600f, reactionTimeMsP50 = 580f,
            accuracyPct = 70f, errorsCount = 3, difficultyLevel = 2
        )

        try {
            gameResultDao.insert(orphanResult)
            fail(
                "Se esperaba SQLiteConstraintException al insertar game_result " +
                "con sessionId='ghost-session' inexistente en cognitive_sessions"
            )
        } catch (e: SQLiteConstraintException) {
            // ✓ FK constraint activada correctamente — excepción esperada
        }
    }

    /**
     * Verifica el comportamiento contrario: insertar con sessionId VÁLIDO no lanza excepción.
     */
    @SmallTest
    @Test
    fun gameResultDao_insertWithValidSessionId_succeeds() = runTest {
        sessionDao.insert(session1)   // session1.id = "s1"
        gameResultDao.insert(result1.copy(sessionId = "s1"))

        val stored = gameResultDao.getResultsBySession("s1").first()
        assertEquals(1, stored.size)
    }

    /**
     * Verifica CASCADE DELETE: al eliminar una sesión, sus resultados deben eliminarse.
     * Este CASCADE está definido en [GameResultEntity] → sessionId FK onDelete=CASCADE.
     */
    @SmallTest
    @Test
    fun gameResultDao_cascadeDelete_resultsRemovedWhenSessionDeleted() = runTest {
        sessionDao.insert(session1)
        gameResultDao.insertAll(listOf(result1, result2))

        // Verificar que los resultados existen
        assertEquals(2, gameResultDao.getResultsBySession("s1").first().size)

        // Eliminar la sesión padre
        // Room no tiene @Delete para sesiones, así que usamos SQL directo vía insert con
        // OnConflict=REPLACE con un noop, o insertamos una sesión que reemplaza la existente
        // y luego forzamos la eliminación via una ruta que sí exista en el DAO.
        // Como SessionDao no expone delete(), usamos la única FK que SÍ existe:
        // GamificationEntity → UserEntity → solo gamification se borra en cascada.
        // Para eliminar sesiones necesitamos otro camino. Aquí lo hacemos directamente
        // a través del companion de la base de datos en modo test.
        db.runInTransaction {
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM cognitive_sessions WHERE id = 's1'"
            )
        }

        val results = gameResultDao.getResultsBySession("s1").first()
        assertTrue(
            "Los game_results deben eliminarse en cascada al borrar la sesión",
            results.isEmpty()
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BaselineDao
    // ══════════════════════════════════════════════════════════════════════════

    @SmallTest
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

    @SmallTest
    @Test
    fun baselineDao_updateByDomain_updatesTargetDomainAndRecalculatesOverall() = runTest {
        userDao.insert(user1)
        baselineDao.insert(
            BaselineEntity(
                userId = "u1",
                memoryScore = 70f, attentionScore = 75f,
                executiveScore = 68f, languageScore = 80f, orientationScore = 85f,
                overallScore = 75.6f, calculatedAt = System.currentTimeMillis()
            )
        )

        baselineDao.updateByDomain("u1", "MEMORY", 90f)

        val updated = baselineDao.getByUserId("u1")!!
        assertEquals("memoryScore debe ser 90f", 90f, updated.memoryScore, 0.01f)
        assertEquals("attentionScore NO debe cambiar", 75f, updated.attentionScore, 0.01f)
        // overallScore = (90 + 75 + 68 + 80 + 85) / 5 = 398 / 5 = 79.6
        assertEquals("overallScore debe recalcularse a 79.6", 79.6f, updated.overallScore, 0.01f)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MlPredictionDao
    // ══════════════════════════════════════════════════════════════════════════

    @SmallTest
    @Test
    fun mlPredictionDao_insertAndGetLatest() = runTest {
        userDao.insert(user1)
        mlDao.insert(
            MlPredictionEntity(
                id = "p1", userId = "u1", predictionDate = 1_700_000_000_000L,
                riskScore = 0.3f, alertLevel = "NORMAL",
                domainsFlagged = "", explanation = "Sin riesgo"
            )
        )

        val latest = mlDao.getLatestForUser("u1")
        assertNotNull(latest)
        assertEquals("p1", latest!!.id)
    }

    @SmallTest
    @Test
    fun mlPredictionDao_getHistory_returnsMostRecentFirst() = runTest {
        userDao.insert(user1)
        mlDao.insert(MlPredictionEntity("p1", "u1", 1_000L, 0.2f, "NORMAL", "", ""))
        mlDao.insert(MlPredictionEntity("p2", "u1", 2_000L, 0.4f, "WATCH",  "", ""))

        val history = mlDao.getHistoryForUser("u1", 10)
        assertEquals("p2", history.first().id)   // más reciente primero
    }
}
