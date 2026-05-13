package com.eternamente.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eternamente.app.data.local.database.entity.BaselineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BaselineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(baseline: BaselineEntity)

    @Update
    suspend fun update(baseline: BaselineEntity)

    @Query("SELECT * FROM cognitive_baselines WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): BaselineEntity?

    @Query("SELECT * FROM cognitive_baselines WHERE userId = :userId LIMIT 1")
    fun observe(userId: String): Flow<BaselineEntity?>

    /**
     * Actualiza la puntuación de un dominio cognitivo individual usando una
     * expresión CASE que toca solo la columna correspondiente.
     *
     * El [overallScore] se recalcula como el promedio de los 5 dominios
     * principales (excluyendo PROCESSING_SPEED, que no forma parte del baseline).
     *
     * @param userId UUID del usuario.
     * @param domain Nombre del dominio (`CognitiveDomain.name`).
     * @param score  Puntuación normalizada (0–100).
     */
    @Query("""
        UPDATE cognitive_baselines
        SET
            memoryScore      = CASE WHEN :domain = 'MEMORY'      THEN :score ELSE memoryScore      END,
            attentionScore   = CASE WHEN :domain = 'ATTENTION'   THEN :score ELSE attentionScore   END,
            executiveScore   = CASE WHEN :domain = 'EXECUTIVE'   THEN :score ELSE executiveScore   END,
            languageScore    = CASE WHEN :domain = 'LANGUAGE'    THEN :score ELSE languageScore    END,
            orientationScore = CASE WHEN :domain = 'ORIENTATION' THEN :score ELSE orientationScore END,
            overallScore = (
                CASE WHEN :domain = 'MEMORY'      THEN :score ELSE memoryScore      END +
                CASE WHEN :domain = 'ATTENTION'   THEN :score ELSE attentionScore   END +
                CASE WHEN :domain = 'EXECUTIVE'   THEN :score ELSE executiveScore   END +
                CASE WHEN :domain = 'LANGUAGE'    THEN :score ELSE languageScore    END +
                CASE WHEN :domain = 'ORIENTATION' THEN :score ELSE orientationScore END
            ) / 5.0
        WHERE userId = :userId
    """)
    suspend fun updateByDomain(userId: String, domain: String, score: Float)
}
