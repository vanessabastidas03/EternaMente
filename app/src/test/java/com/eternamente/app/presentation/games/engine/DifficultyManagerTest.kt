package com.eternamente.app.presentation.games.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para [DifficultyManager].
 *
 * Verifica:
 * - Algoritmo de adaptación (subir / bajar / mantener nivel).
 * - Coerción de límites (MIN / MAX).
 * - Parámetros de la tabla de dificultad.
 * - Operación [advanceLevel] combinada.
 */
class DifficultyManagerTest {

    // ── Subir de nivel ────────────────────────────────────────────────────────

    @Test
    fun `calculateNextLevel sube nivel cuando accuracy supera 80 pct`() {
        val manager = DifficultyManager("test_game", initialLevel = 2)
        val metrics = metricsWithAccuracy(85f)

        val next = manager.calculateNextLevel(metrics)

        assertEquals(3, next)
    }

    @Test
    fun `calculateNextLevel sube nivel exactamente en 80 pct`() {
        val manager = DifficultyManager("test_game", initialLevel = 1)
        val metrics = metricsWithAccuracy(80f)

        assertEquals(2, manager.calculateNextLevel(metrics))
    }

    // ── Bajar de nivel ────────────────────────────────────────────────────────

    @Test
    fun `calculateNextLevel baja nivel cuando accuracy es menor a 50 pct`() {
        val manager = DifficultyManager("test_game", initialLevel = 3)
        val metrics = metricsWithAccuracy(45f)

        assertEquals(2, manager.calculateNextLevel(metrics))
    }

    @Test
    fun `calculateNextLevel baja nivel con accuracy 0 pct`() {
        val manager = DifficultyManager("test_game", initialLevel = 4)
        val metrics = metricsWithAccuracy(0f)

        assertEquals(3, manager.calculateNextLevel(metrics))
    }

    // ── Mantener nivel ────────────────────────────────────────────────────────

    @Test
    fun `calculateNextLevel mantiene nivel con accuracy entre 50 y 80 pct`() {
        val manager = DifficultyManager("test_game", initialLevel = 3)

        listOf(50f, 65f, 79.9f).forEach { acc ->
            val next = manager.calculateNextLevel(metricsWithAccuracy(acc))
            assertEquals("Con accuracy $acc% debería mantener nivel 3", 3, next)
        }
    }

    @Test
    fun `calculateNextLevel mantiene nivel exactamente en 79_9 pct`() {
        val manager = DifficultyManager("test_game", initialLevel = 2)
        assertEquals(2, manager.calculateNextLevel(metricsWithAccuracy(79.9f)))
    }

    // ── Límites ───────────────────────────────────────────────────────────────

    @Test
    fun `nivel nunca supera MAX_LEVEL aunque accuracy sea perfecta`() {
        val manager = DifficultyManager("test_game", initialLevel = DifficultyManager.MAX_LEVEL)
        val metrics = metricsWithAccuracy(100f)

        val next = manager.calculateNextLevel(metrics)

        assertEquals(DifficultyManager.MAX_LEVEL, next)
    }

    @Test
    fun `nivel nunca baja de MIN_LEVEL aunque accuracy sea cero`() {
        val manager = DifficultyManager("test_game", initialLevel = DifficultyManager.MIN_LEVEL)
        val metrics = metricsWithAccuracy(0f)

        val next = manager.calculateNextLevel(metrics)

        assertEquals(DifficultyManager.MIN_LEVEL, next)
    }

    @Test
    fun `initialLevel fuera de rango es coercionado al rango valido`() {
        val below = DifficultyManager("test_game", initialLevel = -5)
        val above = DifficultyManager("test_game", initialLevel = 999)

        assertEquals(DifficultyManager.MIN_LEVEL, below.currentLevel)
        assertEquals(DifficultyManager.MAX_LEVEL, above.currentLevel)
    }

    // ── applyLevel ────────────────────────────────────────────────────────────

    @Test
    fun `applyLevel actualiza currentLevel correctamente`() {
        val manager = DifficultyManager("test_game", initialLevel = 1)
        manager.applyLevel(4)

        assertEquals(4, manager.currentLevel)
        assertEquals(4, manager.currentParams.level)
    }

    @Test
    fun `applyLevel coerciona nivel fuera de rango`() {
        val manager = DifficultyManager("test_game", initialLevel = 3)
        manager.applyLevel(10)
        assertEquals(DifficultyManager.MAX_LEVEL, manager.currentLevel)

        manager.applyLevel(-1)
        assertEquals(DifficultyManager.MIN_LEVEL, manager.currentLevel)
    }

    // ── advanceLevel ─────────────────────────────────────────────────────────

    @Test
    fun `advanceLevel combina calculateNextLevel y applyLevel en un paso`() {
        val manager = DifficultyManager("test_game", initialLevel = 2)
        val metrics = metricsWithAccuracy(90f)

        val params = manager.advanceLevel(metrics)

        assertEquals(3, manager.currentLevel)
        assertEquals(3, params.level)
    }

    // ── Tabla de dificultad ───────────────────────────────────────────────────

    @Test
    fun `tabla de dificultad tiene 5 niveles`() {
        assertEquals(5, DifficultyManager.DIFFICULTY_TABLE.size)
    }

    @Test
    fun `nivel 1 tiene el tiempo limite mas generoso`() {
        val level1 = DifficultyManager.DIFFICULTY_TABLE[0]
        val level5 = DifficultyManager.DIFFICULTY_TABLE[4]

        assertTrue(
            "Nivel 1 debe tener más tiempo que nivel 5",
            (level1.timeLimit ?: Int.MAX_VALUE) > (level5.timeLimit ?: 0)
        )
    }

    @Test
    fun `nivel 5 tiene mas items que nivel 1`() {
        val level1 = DifficultyManager.DIFFICULTY_TABLE[0]
        val level5 = DifficultyManager.DIFFICULTY_TABLE[4]

        assertTrue(level5.itemCount > level1.itemCount)
    }

    @Test
    fun `nivel 5 tiene mas distractores que nivel 1`() {
        val level1 = DifficultyManager.DIFFICULTY_TABLE[0]
        val level5 = DifficultyManager.DIFFICULTY_TABLE[4]

        assertTrue(level5.distractors > level1.distractors)
    }

    @Test
    fun `velocidad aumenta con el nivel`() {
        val table = DifficultyManager.DIFFICULTY_TABLE
        for (i in 0 until table.size - 1) {
            assertTrue(
                "Nivel ${table[i + 1].level} debe ser más rápido que nivel ${table[i].level}",
                table[i + 1].speed >= table[i].speed
            )
        }
    }

    @Test
    fun `calculateNextLevel no muta el estado interno`() {
        val manager = DifficultyManager("test_game", initialLevel = 3)
        val originalLevel = manager.currentLevel

        // Calcular sin aplicar
        manager.calculateNextLevel(metricsWithAccuracy(90f))
        manager.calculateNextLevel(metricsWithAccuracy(10f))

        assertEquals(
            "calculateNextLevel no debe mutar currentLevel",
            originalLevel,
            manager.currentLevel
        )
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun metricsWithAccuracy(accuracyPct: Float): MetricsSnapshot {
        val correct = (accuracyPct / 100f * 20).toInt()
        val total   = 20
        return MetricsSnapshot(
            reactionTimes = List(total) { 300f },
            mean          = 300f,
            median        = 300f,
            p90           = 380f,
            accuracyPct   = accuracyPct,
            correctCount  = correct,
            errorCount    = total - correct,
            omissionCount = 0,
            totalTrials   = total
        )
    }
}
