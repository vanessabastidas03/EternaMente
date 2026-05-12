package com.eternamente.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.eternamente.app.data.local.db.dao.GameResultDao
import com.eternamente.app.data.local.db.dao.GamificationDao
import com.eternamente.app.data.local.db.dao.MlPredictionDao
import com.eternamente.app.data.local.db.dao.SessionDao
import com.eternamente.app.data.local.db.dao.UserDao
import com.eternamente.app.data.local.db.entity.CognitiveSessionEntity
import com.eternamente.app.data.local.db.entity.GameResultEntity
import com.eternamente.app.data.local.db.entity.GamificationProfileEntity
import com.eternamente.app.data.local.db.entity.MlPredictionEntity
import com.eternamente.app.data.local.db.entity.UserEntity

/**
 * Room database for EternaMente, encrypted at rest with SQLCipher (AES-256).
 *
 * The [net.sqlcipher.database.SupportFactory] is supplied in [com.eternamente.app.di.DatabaseModule];
 * this class has no knowledge of the encryption key.
 *
 * Schema export is enabled: migration scripts live in `app/schemas/`.
 * Add `AutoMigration` entries in [autoMigrations] when bumping [version].
 */
@Database(
    entities = [
        UserEntity::class,
        CognitiveSessionEntity::class,
        GameResultEntity::class,
        MlPredictionEntity::class,
        GamificationProfileEntity::class
    ],
    version   = 1,
    exportSchema = true
)
abstract class EternaDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun gameResultDao(): GameResultDao
    abstract fun mlPredictionDao(): MlPredictionDao
    abstract fun gamificationDao(): GamificationDao

    companion object {
        const val DATABASE_NAME = "eternamente.db"
    }
}
