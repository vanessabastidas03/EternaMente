package com.eternamente.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.eternamente.app.data.local.database.entity.GameResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: GameResultEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<GameResultEntity>)

    /** Flow reactivo de resultados de una sesión específica. */
    @Query("SELECT * FROM game_results WHERE sessionId = :sessionId ORDER BY rowid ASC")
    fun getResultsBySession(sessionId: String): Flow<List<GameResultEntity>>

    /**
     * Resultados del usuario filtrados por dominio cognitivo, más recientes primero.
     *
     * Usa el índice `(userId, domain)` para evitar full-table scan.
     *
     * @param userId UUID del usuario.
     * @param domain Nombre del [com.eternamente.app.domain.model.CognitiveDomain].
     * @param limit  Número máximo de resultados a retornar.
     */
    @Query("""
        SELECT * FROM game_results
        WHERE userId = :userId AND domain = :domain
        ORDER BY rowid DESC
        LIMIT :limit
    """)
    suspend fun getResultsByDomain(userId: String, domain: String, limit: Int): List<GameResultEntity>

    /**
     * Promedio de [GameResultEntity.scoreNormalized] agrupado por dominio
     * para sesiones dentro del rango de tiempo especificado.
     *
     * Llamar con `fromEpochMs = now - weeksBack * 7 * 24 * 3600 * 1000` desde
     * el repositorio para calcular promedios semanales.
     *
     * @param userId      UUID del usuario.
     * @param fromEpochMs Inicio del rango en epoch-ms.
     */
    @Transaction
    @Query("""
        SELECT gr.domain       AS domain,
               AVG(gr.scoreNormalized) AS avgScore
        FROM game_results gr
        INNER JOIN cognitive_sessions cs ON gr.sessionId = cs.id
        WHERE gr.userId = :userId
          AND cs.sessionDate >= :fromEpochMs
        GROUP BY gr.domain
    """)
    suspend fun getAveragesByDomainSince(userId: String, fromEpochMs: Long): List<DomainAvgRow>

    /**
     * N resultados más recientes del usuario en todos los dominios.
     * Usados como input para el pipeline de features del modelo TFLite.
     */
    @Query("""
        SELECT * FROM game_results
        WHERE userId = :userId
        ORDER BY rowid DESC
        LIMIT :limit
    """)
    suspend fun getLatestResults(userId: String, limit: Int): List<GameResultEntity>

    /**
     * Evolución histórica de puntuación para un juego específico.
     * Usada para gráficos de tendencia (sparkline) por partida.
     */
    @Query("""
        SELECT gr.scoreNormalized
        FROM game_results gr
        INNER JOIN cognitive_sessions cs ON gr.sessionId = cs.id
        WHERE gr.userId = :userId AND gr.gameId = :gameId
        ORDER BY cs.sessionDate DESC
        LIMIT :limit
    """)
    suspend fun getScoreTrend(userId: String, gameId: String, limit: Int): List<Float>

    /**
     * Cuenta todos los resultados de partidas jugadas hoy por el usuario,
     * independientemente de en cuántas sesiones se hayan distribuido.
     * Usado por el widget de progreso del Dashboard.
     */
    @Query("""
        SELECT COUNT(*) FROM game_results gr
        INNER JOIN cognitive_sessions cs ON gr.sessionId = cs.id
        WHERE gr.userId = :userId AND cs.sessionDate >= :fromEpochMs
    """)
    suspend fun countGameResultsForUserToday(userId: String, fromEpochMs: Long): Int

    /** Proyección usada por [getAveragesByDomainSince]. */
    data class DomainAvgRow(val domain: String, val avgScore: Float)
}
