package com.eternamente.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eternamente.app.data.local.database.entity.MlPredictionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MlPredictionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prediction: MlPredictionEntity)

    /** Predicción más reciente del usuario; `null` si nunca se ha calculado. */
    @Query("""
        SELECT * FROM ml_predictions
        WHERE userId = :userId
        ORDER BY predictionDate DESC
        LIMIT 1
    """)
    suspend fun getLatestForUser(userId: String): MlPredictionEntity?

    /**
     * Historial de predicciones del usuario, más reciente primero.
     *
     * @param limit Número máximo de predicciones a retornar.
     */
    @Query("""
        SELECT * FROM ml_predictions
        WHERE userId = :userId
        ORDER BY predictionDate DESC
        LIMIT :limit
    """)
    suspend fun getHistoryForUser(userId: String, limit: Int): List<MlPredictionEntity>

    /** Flow reactivo del historial completo; actualiza al insertar nuevas predicciones. */
    @Query("""
        SELECT * FROM ml_predictions
        WHERE userId = :userId
        ORDER BY predictionDate DESC
    """)
    fun observeHistory(userId: String): Flow<List<MlPredictionEntity>>

    @Query("DELETE FROM ml_predictions WHERE userId = :userId AND predictionDate < :beforeMs")
    suspend fun deleteOlderThan(userId: String, beforeMs: Long): Int
}
