package com.eternamente.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.eternamente.app.data.local.database.entity.GamificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GamificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: GamificationEntity)

    @Update
    suspend fun update(profile: GamificationEntity)

    @Query("SELECT * FROM gamification WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): GamificationEntity?

    @Query("SELECT * FROM gamification WHERE userId = :userId LIMIT 1")
    fun observe(userId: String): Flow<GamificationEntity?>

    /**
     * Incrementa [GamificationEntity.currentStreak] en 1 y actualiza
     * [GamificationEntity.maxStreak] si se supera el máximo histórico.
     *
     * @param userId      UUID del usuario.
     * @param sessionDate Fecha ISO-8601 de la sesión completada (ej. `"2024-03-15"`).
     */
    @Query("""
        UPDATE gamification
        SET currentStreak   = currentStreak + 1,
            maxStreak       = MAX(maxStreak, currentStreak + 1),
            lastSessionDate = :sessionDate
        WHERE userId = :userId
    """)
    suspend fun incrementStreak(userId: String, sessionDate: String)

    /**
     * Restablece [GamificationEntity.currentStreak] a 0 cuando el usuario
     * pierde la racha (no completó sesión el día anterior).
     *
     * [GamificationEntity.maxStreak] NO se modifica — preserva el histórico.
     */
    @Query("UPDATE gamification SET currentStreak = 0 WHERE userId = :userId")
    suspend fun resetStreak(userId: String)

    @Query("""
        UPDATE gamification
        SET totalPoints = totalPoints + :points
        WHERE userId = :userId
    """)
    suspend fun addPoints(userId: String, points: Int)

    /**
     * Añade un badge (nombre) a la lista almacenada si no está ya presente.
     * La comprobación de duplicados se hace en el repositorio antes de llamar esto.
     */
    @Query("""
        UPDATE gamification
        SET badges = CASE
            WHEN badges = ''      THEN :badgeName
            ELSE badges || ',' || :badgeName
        END
        WHERE userId = :userId
    """)
    suspend fun appendBadge(userId: String, badgeName: String)

    @Transaction
    suspend fun addPointsAndIncrementStreak(userId: String, points: Int, sessionDate: String) {
        addPoints(userId, points)
        incrementStreak(userId, sessionDate)
    }
}
