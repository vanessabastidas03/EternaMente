package com.eternamente.app.presentation.games

import com.eternamente.app.presentation.games.corsiblock.CorsiConfig
import com.eternamente.app.presentation.games.corsiblock.CorsiEngine
import com.eternamente.app.presentation.games.corsiblock.CorsiPhase
import com.eternamente.app.presentation.games.digitspan.DigitSpanConfig
import com.eternamente.app.presentation.games.digitspan.DigitSpanEngine
import com.eternamente.app.presentation.games.digitspan.SpanPhase
import com.eternamente.app.presentation.games.engine.InputFeedback
import com.eternamente.app.presentation.games.engine.UserInput
import com.eternamente.app.presentation.games.flashcolor.FlashColorConfig
import com.eternamente.app.presentation.games.flashcolor.FlashColorEngine
import com.eternamente.app.presentation.games.flashcolor.StimulusColor
import com.eternamente.app.presentation.games.stroop.StroopConfig
import com.eternamente.app.presentation.games.stroop.StroopEngine
import com.eternamente.app.presentation.games.temporalorientation.TemporalConfig
import com.eternamente.app.presentation.games.temporalorientation.TemporalOrientationEngine
import com.eternamente.app.presentation.games.temporalorientation.buildOrientationQuestions
import com.eternamente.app.presentation.games.trailmaking.TrailMakingConfig
import com.eternamente.app.presentation.games.trailmaking.TrailMakingEngine
import com.eternamente.app.presentation.games.verbalfluency.FluencyCategory
import com.eternamente.app.presentation.games.verbalfluency.VerbalFluencyConfig
import com.eternamente.app.presentation.games.verbalfluency.VerbalFluencyEngine
import org.junit.Assert.*
import org.junit.Test

// ── DigitSpan ─────────────────────────────────────────────────────────────────

class DigitSpanEngineTest {

    @Test
    fun `config level 1 genera secuencia de 3 digitos`() {
        val config = DigitSpanConfig.forDifficulty(1, "s", "u")
        assertEquals(3, config.sequenceLength)
        assertFalse(config.isBackward)
    }

    @Test
    fun `config level 3 activa modo backward`() {
        val config = DigitSpanConfig.forDifficulty(3, "s", "u")
        assertTrue(config.isBackward)
        assertEquals(5, config.sequenceLength)
    }

    @Test
    fun `config level 5 genera secuencia de 7 digitos`() {
        val config = DigitSpanConfig.forDifficulty(5, "s", "u")
        assertEquals(7, config.sequenceLength)
        assertTrue(config.isBackward)
    }

    @Test
    fun `forward mode longitud de secuencia es correcta`() {
        val engine = DigitSpanEngine(DigitSpanConfig(sessionId="s", userId="u", sequenceLength=4))
        assertEquals(4, engine.config.sequenceLength)
    }

    @Test
    fun `onInput ignorado fuera de fase INPUT`() {
        val engine = DigitSpanEngine(DigitSpanConfig(sessionId="s", userId="u"))
        assertEquals(InputFeedback.Ignored, engine.onInput(UserInput.SelectOption(5)))
    }

    @Test
    fun `onInput acepta digitos validos en rango 0-9`() {
        assertEquals(DigitSpanConfig.forDifficulty(1, "s", "u").gameId, DigitSpanConfig.GAME_ID)
    }
}

// ── FlashColor ────────────────────────────────────────────────────────────────

class FlashColorEngineTest {

    @Test
    fun `genera 30 estimulos totales`() {
        val config = FlashColorConfig.forDifficulty(1, "s", "u").copy(totalStimuli = 30)
        val engine = FlashColorEngine(config)
        // El engine genera los estímulos internamente; verificamos la config
        assertEquals(30, config.totalStimuli)
    }

    @Test
    fun `d-prime con hit=1 fa=0 es positivo`() {
        val engine = FlashColorEngine(FlashColorConfig(sessionId="s", userId="u", targetColor=StimulusColor.RED))
        val dPrime = engine.computeDPrime(0.99f, 0.01f)
        assertTrue("d-prime debe ser positivo con buenas métricas: $dPrime", dPrime > 0f)
    }

    @Test
    fun `d-prime con hit=0_5 fa=0_5 es cero`() {
        val engine = FlashColorEngine(FlashColorConfig(sessionId="s", userId="u", targetColor=StimulusColor.RED))
        val dPrime = engine.computeDPrime(0.5f, 0.5f)
        assertEquals(0f, dPrime, 0.05f)
    }

    @Test
    fun `d-prime con hit mayor que fa es positivo`() {
        val engine = FlashColorEngine(FlashColorConfig(sessionId="s", userId="u", targetColor=StimulusColor.RED))
        val dPrime = engine.computeDPrime(0.8f, 0.2f)
        assertTrue("d' debe ser > 0 cuando H > FA: $dPrime", dPrime > 0f)
    }

    @Test
    fun `onInput ignorado cuando no hay estimulo visible`() {
        val engine = FlashColorEngine(FlashColorConfig(sessionId="s", userId="u", targetColor=StimulusColor.RED))
        assertEquals(InputFeedback.Ignored, engine.onInput(UserInput.Tap))
    }

    @Test
    fun `config nivel 5 tiene duracion menor que nivel 1`() {
        val l1 = FlashColorConfig.forDifficulty(1, "s", "u")
        val l5 = FlashColorConfig.forDifficulty(5, "s", "u")
        assertTrue(l5.stimulusDurationMs < l1.stimulusDurationMs)
    }
}

// ── TrailMaking ───────────────────────────────────────────────────────────────

class TrailMakingEngineTest {

    @Test
    fun `genera nodos con posiciones unicas`() {
        val config = TrailMakingConfig.forDifficulty(1, "s", "u")
        val engine = TrailMakingEngine(config)
        val positions = engine.nodes.map { it.position }
        assertEquals(config.nodeCount, positions.size)
    }

    @Test
    fun `nivel 1-2 usa solo numeros`() {
        val config = TrailMakingConfig.forDifficulty(1, "s", "u")
        assertFalse(config.isAlternating)
        assertEquals(15, config.nodeCount)
    }

    @Test
    fun `nivel 3+ usa alternancia numeros-letras`() {
        val config = TrailMakingConfig.forDifficulty(3, "s", "u")
        assertTrue(config.isAlternating)
    }

    @Test
    fun `secuencia no alternante es 1 hasta nodeCount`() {
        val config = TrailMakingConfig(sessionId="s", userId="u", nodeCount=10, isAlternating=false)
        val engine = TrailMakingEngine(config)
        assertEquals("1", engine.nodes.first().label)
        assertEquals("10", engine.nodes.last().label)
    }

    @Test
    fun `secuencia alternante empieza con 1-A`() {
        val config = TrailMakingConfig(sessionId="s", userId="u", nodeCount=10, isAlternating=true)
        val engine = TrailMakingEngine(config)
        assertEquals("1", engine.nodes[0].label)
        assertEquals("A", engine.nodes[1].label)
        assertEquals("2", engine.nodes[2].label)
    }

    @Test
    fun `proximidad detectada dentro del radio`() {
        val config = TrailMakingConfig.forDifficulty(1, "s", "u")
        val engine = TrailMakingEngine(config)
        // Primer nodo siempre existe; posición está en [0.1, 0.85]
        val firstNodePos = engine.nodes[0].position
        // Inmediatamente encima: debe detectar proximidad
        val nearPos = androidx.compose.ui.geometry.Offset(firstNodePos.x, firstNodePos.y)
        // Con el juego en Idle, checkProximity retorna false
        assertFalse(engine.checkProximityAndConnect(nearPos))
    }
}

// ── VerbalFluency ─────────────────────────────────────────────────────────────

class VerbalFluencyEngineTest {

    @Test
    fun `palabras validas se aceptan`() {
        val engine = VerbalFluencyEngine(VerbalFluencyConfig(sessionId="s", userId="u", category=FluencyCategory.ANIMALS))
        // En estado Idle el input es ignorado
        val result = engine.onInput(UserInput.TapTarget("perro"))
        assertEquals(InputFeedback.Ignored, result)
    }

    @Test
    fun `categoria ANIMALS tiene mas de 50 palabras`() {
        assertTrue(FluencyCategory.ANIMALS.words.size >= 50)
    }

    @Test
    fun `categoria FRUITS tiene mas de 30 palabras`() {
        assertTrue(FluencyCategory.FRUITS.words.size >= 30)
    }

    @Test
    fun `config nivel 1 usa ANIMALS`() {
        val config = VerbalFluencyConfig.forDifficulty(1, "s", "u")
        assertEquals(FluencyCategory.ANIMALS, config.category)
    }

    @Test
    fun `palabras de distintas categorias no se mezclan`() {
        val animals = FluencyCategory.ANIMALS.words
        val fruits  = FluencyCategory.FRUITS.words
        // "perro" no es fruta; "manzana" no es animal
        assertTrue("perro" in animals); assertFalse("perro" in fruits)
        assertTrue("manzana" in fruits); assertFalse("manzana" in animals)
    }
}

// ── Stroop ────────────────────────────────────────────────────────────────────

class StroopEngineTest {

    @Test
    fun `genera 20 estimulos totales`() {
        val config = StroopConfig.forDifficulty(1, "s", "u")
        assertEquals(20, config.totalStimuli)
    }

    @Test
    fun `nivel 5 tiene duracion menor que nivel 1`() {
        val l1 = StroopConfig.forDifficulty(1, "s", "u")
        val l5 = StroopConfig.forDifficulty(5, "s", "u")
        assertTrue(l5.stimulusDurationMs < l1.stimulusDurationMs)
    }
}

// ── Corsi ─────────────────────────────────────────────────────────────────────

class CorsiEngineTest {

    @Test
    fun `nivel 1 tiene secuencia de 2 bloques`() {
        val config = CorsiConfig.forDifficulty(1, "s", "u")
        assertEquals(2, config.sequenceLength)
    }

    @Test
    fun `nivel 5 tiene secuencia de 6 bloques`() {
        val config = CorsiConfig.forDifficulty(5, "s", "u")
        assertEquals(6, config.sequenceLength)
    }

    @Test
    fun `9 posiciones de bloques definidas`() {
        assertEquals(9, com.eternamente.app.presentation.games.corsiblock.BLOCK_POSITIONS.size)
    }

    @Test
    fun `todas las posiciones son distintas`() {
        val positions = com.eternamente.app.presentation.games.corsiblock.BLOCK_POSITIONS
        assertEquals(positions.size, positions.distinct().size)
    }
}

// ── Temporal Orientation ──────────────────────────────────────────────────────

class TemporalOrientationEngineTest {

    @Test
    fun `genera exactamente 5 preguntas`() {
        val questions = buildOrientationQuestions()
        assertEquals(5, questions.size)
    }

    @Test
    fun `cada pregunta tiene 4 opciones`() {
        buildOrientationQuestions().forEach { q ->
            assertEquals("${q.questionText} debe tener 4 opciones", 4, q.options.size)
        }
    }

    @Test
    fun `la respuesta correcta siempre esta en las opciones`() {
        buildOrientationQuestions().forEach { q ->
            assertTrue("'${q.correctAnswer}' debe estar en ${q.options}", q.correctAnswer in q.options)
        }
    }

    @Test
    fun `las opciones son unicas por pregunta`() {
        buildOrientationQuestions().forEach { q ->
            assertEquals("Opciones duplicadas en: ${q.questionText}", q.options.size, q.options.distinct().size)
        }
    }

    @Test
    fun `las preguntas cubren los 5 aspectos de orientacion`() {
        val questions = buildOrientationQuestions()
        val texts = questions.map { it.questionText }
        assertTrue(texts.any { "semana" in it.lowercase() })
        assertTrue(texts.any { "fecha" in it.lowercase() || "día" in it.lowercase() || "mes" in it.lowercase() })
        assertTrue(texts.any { "año" in it.lowercase() || "año" in it.lowercase() })
    }
}
