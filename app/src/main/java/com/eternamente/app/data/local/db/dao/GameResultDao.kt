package com.eternamente.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eternamente.app.data.local.db.entity.GameResultEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for [GameResultEntity] persistence and analytics queries. */
@Dao
interface GameResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: GameResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<GameResultEntity>)

    @Query("SELECT * FROM game_results WHERE sessionId = :sessionId ORDER BY rowid ASC")
    fun observeResultsForSession(sessionId: String): Flow<List<GameResultEntity>>

    @Query("""
        SELECT gr.* FROM game_results gr
        INNER JOIN cognitive_sessions cs ON gr.sessionId = cs.id
        WHERE cs.userId = :userId AND gr.domain = :domain
        ORDER BY cs.sessionDate DESC
    """)
    suspend fun getResultsByDomain(userId: String, domain: String): List<GameResultEntity>

    @Query("""
        SELECT gr.* FROM game_results gr
        INNER JOIN cognitive_sessions cs ON gr.sessionId = cs.id
        WHERE cs.userId = :userId
        ORDER BY cs.sessionDate DESC
        LIMIT :limit
    """)
    suspend fun getLatestResults(userId: String, limit: Int): List<GameResultEntity>

    @Query("""
        SELECT gr.domain AS domain, AVG(gr.scoreNormalized) AS avgScore
        FROM game_results gr
        INNER JOIN cognitive_sessions cs ON gr.sessionId = cs.id
        WHERE cs.userId = :userId
          AND cs.id IN (
              SELECT id FROM cognitive_sessions
              WHERE userId = :userId AND completed = 1
              ORDER BY sessionDate DESC
              LIMIT :windowSessions
          )
        GROUP BY gr.domain
    """)
    suspend fun getAverageScoresByDomain(userId: String, windowSessions: Int): List<DomainAvgRow>

    @Query("""
        SELECT gr.scoreNormalized FROM game_results gr
        INNER JOIN cognitive_sessions cs ON gr.sessionId = cs.id
        WHERE cs.userId = :userId AND gr.gameId = :gameId
        ORDER BY cs.sessionDate DESC
        LIMIT :limit
    """)
    suspend fun getScoreTrendForGame(userId: String, gameId: String, limit: Int): List<Float>

    /** Projection returned by [getAverageScoresByDomain]. */
    data class DomainAvgRow(val domain: String, val avgScore: Float)
}
