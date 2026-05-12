package com.eternamente.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eternamente.app.data.local.db.entity.GamificationProfileEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for [GamificationProfileEntity] persistence and reactive observation. */
@Dao
interface GamificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: GamificationProfileEntity)

    @Update
    suspend fun updateProfile(profile: GamificationProfileEntity)

    @Query("SELECT * FROM gamification_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): GamificationProfileEntity?

    @Query("SELECT * FROM gamification_profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<GamificationProfileEntity?>
}
