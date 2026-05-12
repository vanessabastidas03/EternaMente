package com.eternamente.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eternamente.app.data.local.db.entity.MlPredictionEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for [MlPredictionEntity] persistence and history queries. */
@Dao
interface MlPredictionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrediction(prediction: MlPredictionEntity)

    @Query("""
        SELECT * FROM ml_predictions
        WHERE userId = :userId
        ORDER BY predictionDate DESC
        LIMIT 1
    """)
    suspend fun getLatestPrediction(userId: String): MlPredictionEntity?

    @Query("""
        SELECT * FROM ml_predictions
        WHERE userId = :userId
        ORDER BY predictionDate DESC
    """)
    fun observePredictionHistory(userId: String): Flow<List<MlPredictionEntity>>

    /** Returns the number of rows deleted (used for data-retention reporting). */
    @Query("""
        DELETE FROM ml_predictions
        WHERE userId = :userId AND predictionDate < :beforeEpochMs
    """)
    suspend fun deletePredictionsBefore(userId: String, beforeEpochMs: Long): Int
}
