package com.eternamente.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.eternamente.app.data.local.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM cognitive_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    /**
     * Retorna un Flow de sesiones del usuario dentro de un rango de fechas.
     *
     * @param userId      UUID del usuario.
     * @param fromEpochMs Inicio del rango en epoch-ms (inclusive).
     * @param toEpochMs   Fin del rango en epoch-ms (inclusive).
     */
    @Transaction
    @Query("""
        SELECT * FROM cognitive_sessions
        WHERE userId = :userId
          AND sessionDate BETWEEN :fromEpochMs AND :toEpochMs
        ORDER BY sessionDate DESC
    """)
    fun getSessionsForUser(
        userId: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): Flow<List<SessionEntity>>

    /** Todas las sesiones del usuario, ordenadas de más reciente a más antigua. */
    @Query("""
        SELECT * FROM cognitive_sessions
        WHERE userId = :userId
        ORDER BY sessionDate DESC
    """)
    fun observeAll(userId: String): Flow<List<SessionEntity>>

    /** Última sesión completada o en progreso del usuario. */
    @Query("""
        SELECT * FROM cognitive_sessions
        WHERE userId = :userId
        ORDER BY sessionDate DESC LIMIT 1
    """)
    suspend fun getLastSession(userId: String): SessionEntity?

    /** Número de sesiones completadas del usuario. */
    @Query("""
        SELECT COUNT(*) FROM cognitive_sessions
        WHERE userId = :userId AND completed = 1
    """)
    suspend fun getSessionCount(userId: String): Int

    @Query("""
        UPDATE cognitive_sessions
        SET completed = 1, durationSeconds = :durationSeconds
        WHERE id = :id
    """)
    suspend fun markCompleted(id: String, durationSeconds: Int)

    @Query("""
        SELECT COUNT(*) FROM cognitive_sessions
        WHERE userId = :userId AND type = 'BASELINE' AND completed = 1
    """)
    suspend fun countCompletedBaselines(userId: String): Int

    @Query("""
        SELECT COUNT(*) FROM cognitive_sessions
        WHERE userId = :userId AND completed = 1
          AND sessionDate BETWEEN :fromEpochMs AND :toEpochMs
    """)
    suspend fun countCompletedInRange(userId: String, fromEpochMs: Long, toEpochMs: Long): Int
}
