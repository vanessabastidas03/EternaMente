package com.eternamente.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eternamente.app.data.local.db.entity.CognitiveSessionEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for [CognitiveSessionEntity] persistence and lifecycle queries. */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CognitiveSessionEntity)

    @Query("SELECT * FROM cognitive_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): CognitiveSessionEntity?

    @Query("""
        SELECT * FROM cognitive_sessions
        WHERE userId = :userId
        ORDER BY sessionDate DESC
    """)
    fun observeSessionsForUser(userId: String): Flow<List<CognitiveSessionEntity>>

    @Query("""
        SELECT * FROM cognitive_sessions
        WHERE userId = :userId AND type = :type
        ORDER BY sessionDate DESC
    """)
    suspend fun getSessionsByType(userId: String, type: String): List<CognitiveSessionEntity>

    @Query("""
        UPDATE cognitive_sessions
        SET completed = 1, durationSeconds = :durationSeconds
        WHERE id = :sessionId
    """)
    suspend fun markCompleted(sessionId: String, durationSeconds: Int)

    @Query("SELECT * FROM cognitive_sessions WHERE userId = :userId ORDER BY sessionDate DESC LIMIT 1")
    suspend fun getLatestSession(userId: String): CognitiveSessionEntity?

    @Query("""
        SELECT COUNT(*) FROM cognitive_sessions
        WHERE userId = :userId
          AND completed = 1
          AND sessionDate BETWEEN :fromEpochMs AND :toEpochMs
    """)
    suspend fun countCompletedSessions(userId: String, fromEpochMs: Long, toEpochMs: Long): Int

    @Query("""
        SELECT COUNT(*) FROM cognitive_sessions
        WHERE userId = :userId AND type = 'BASELINE' AND completed = 1
    """)
    suspend fun countCompletedBaselines(userId: String): Int
}
