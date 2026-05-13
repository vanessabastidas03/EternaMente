package com.eternamente.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eternamente.app.data.local.database.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: SettingsEntity)

    @Update
    suspend fun update(settings: SettingsEntity)

    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): SettingsEntity?

    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    fun observe(userId: String): Flow<SettingsEntity?>
}
