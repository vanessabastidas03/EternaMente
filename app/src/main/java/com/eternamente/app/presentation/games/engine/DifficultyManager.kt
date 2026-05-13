package com.eternamente.app.presentation.games.engine

/**
 * Parámetros que definen un nivel de dificultad en un juego cognitivo.
 *
 * @property level       Número de nivel (1 = más fácil, 5 = más difícil).
 * @property timeLimit   Segundos límite por juego; `null` = sin límite.
 * @property itemCount   Número de estímulos o ítems a presentar.
 * @property speed       Multiplicador de velocidad relativa (1.0 = normal).
 * @property distractors Número de estímulos distractores por ensayo.
 */
data class DifficultyParams(
    val level: Int,
    val timeLimit: Int?,
    val itemCount: Int,
    val speed: Float,
    val distractors: Int
)

/**
 * Gestor de dificultad adaptativa para juegos cognitivos.
 *
 * ## Algoritmo de adaptación
 * - Si [MetricsSnapshot.accuracyPct] ≥ [HIGH_ACCURACY_THRESHOLD] → subir nivel.
 * - Si [MetricsSnapshot.accuracyPct] < [LOW_ACCURACY_THRESHOLD]  → bajar nivel.
 * - Cualquier otro valor → mantener nivel actual.
 *
 * Los niveles siempre se mantienen en [[MIN_LEVEL], [MAX_LEVEL]].
 *
 * ## Parámetros por defecto
 * Los parámetros están diseñados para maximizar el discriminante de señal de
 * deterioro cognitivo leve (DCL) basado en literature de neuropsicología:
 * nivel bajo → estímulos simples con tiempo generoso; nivel alto → estímulos
 * complejos con distractores y tiempo reducido.
 *
 * @param gameId       Identificador del juego (reservado para futura customización per-game).
 * @param initialLevel Nivel de inicio (coercionado a [[MIN_LEVEL], [MAX_LEVEL]]).
 */
class DifficultyManager(
    @Suppress("unused") private val gameId: String,
    initialLevel: Int = DEFAULT_INITIAL_LEVEL
) {
    private var _currentLevel: Int = initialLevel.coerceIn(MIN_LEVEL, MAX_LEVEL)

    /** Nivel activo actual. */
    val currentLevel: Int get() = _currentLevel

    /** Parámetros del nivel activo. */
    val currentParams: DifficultyParams get() = DIFFICULTY_TABLE[_currentLevel - 1]

    /**
     * Calcula el nivel para el siguiente ensayo basándose en el rendimiento actual.
     *
     * No muta el estado interno — el llamador decide si aplicar el cambio con [applyLevel].
     *
     * @param performance Métricas del juego terminado o del ensayo actual.
     * @return El nivel recomendado (puede ser igual al actual si no hay cambio).
     */
    fun calculateNextLevel(performance: MetricsSnapshot): Int {
        val next = when {
            performance.accuracyPct >= HIGH_ACCURACY_THRESHOLD && _currentLevel < MAX_LEVEL ->
                _currentLevel + 1
            performance.accuracyPct < LOW_ACCURACY_THRESHOLD && _currentLevel > MIN_LEVEL ->
                _currentLevel - 1
            else ->
                _currentLevel
        }
        return next.coerceIn(MIN_LEVEL, MAX_LEVEL)
    }

    /**
     * Aplica el nivel calculado por [calculateNextLevel] y actualiza [currentParams].
     *
     * @param level Nivel a aplicar. Fuera de rango se coerciona al rango válido.
     */
    fun applyLevel(level: Int) {
        _currentLevel = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
    }

    /**
     * Aplica [calculateNextLevel] y retorna los nuevos parámetros en un solo paso.
     *
     * Equivalente a llamar [calculateNextLevel] + [applyLevel] + [currentParams].
     */
    fun advanceLevel(performance: MetricsSnapshot): DifficultyParams {
        applyLevel(calculateNextLevel(performance))
        return currentParams
    }

    companion object {
        const val MIN_LEVEL               = 1
        const val MAX_LEVEL               = 5
        const val DEFAULT_INITIAL_LEVEL   = 1
        const val HIGH_ACCURACY_THRESHOLD = 80f   // ≥80% → subir nivel
        const val LOW_ACCURACY_THRESHOLD  = 50f   // <50% → bajar nivel

        /**
         * Tabla de dificultad por defecto válida para todos los juegos.
         *
         * Cada juego puede sobreescribir los parámetros heredando esta clase o
         * inyectando su propia tabla mediante el constructor (uso futuro).
         */
        val DIFFICULTY_TABLE: List<DifficultyParams> = listOf(
            DifficultyParams(level = 1, timeLimit = 90, itemCount = 4,  speed = 0.70f, distractors = 0),
            DifficultyParams(level = 2, timeLimit = 75, itemCount = 6,  speed = 0.85f, distractors = 1),
            DifficultyParams(level = 3, timeLimit = 60, itemCount = 8,  speed = 1.00f, distractors = 2),
            DifficultyParams(level = 4, timeLimit = 45, itemCount = 10, speed = 1.25f, distractors = 3),
            DifficultyParams(level = 5, timeLimit = 30, itemCount = 12, speed = 1.50f, distractors = 5)
        )
    }
}
