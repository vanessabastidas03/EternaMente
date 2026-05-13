package com.eternamente.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eternamente.app.data.local.db.dao.GameResultDao
import com.eternamente.app.data.local.db.dao.GamificationDao
import com.eternamente.app.data.local.db.dao.MlPredictionDao
import com.eternamente.app.data.local.db.dao.SessionDao
import com.eternamente.app.data.local.db.dao.UserCredentialsDao
import com.eternamente.app.data.local.db.dao.UserDao
import com.eternamente.app.data.local.db.entity.CognitiveSessionEntity
import com.eternamente.app.data.local.db.entity.GameResultEntity
import com.eternamente.app.data.local.db.entity.GamificationProfileEntity
import com.eternamente.app.data.local.db.entity.MlPredictionEntity
import com.eternamente.app.data.local.db.entity.UserCredentialsEntity
import com.eternamente.app.data.local.db.entity.UserEntity

/**
 * Base de datos Room cifrada con SQLCipher (AES-256).
 *
 * ## Historial de migraciones
 * - v1 → v2: añade columna `email` a `users` + tabla `user_credentials`
 *   para autenticación local con PIN hasheado.
 *
 * Schema export: `app/schemas/`
 * Migrations: usar [MIGRATION_1_2] en [com.eternamente.app.di.DatabaseModule].
 */
@Database(
    entities = [
        UserEntity::class,
        CognitiveSessionEntity::class,
        GameResultEntity::class,
        MlPredictionEntity::class,
        GamificationProfileEntity::class,
        UserCredentialsEntity::class        // v2 — autenticación local
    ],
    version      = 2,
    exportSchema = true
)
abstract class EternaDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun gameResultDao(): GameResultDao
    abstract fun mlPredictionDao(): MlPredictionDao
    abstract fun gamificationDao(): GamificationDao
    abstract fun userCredentialsDao(): UserCredentialsDao  // v2

    companion object {
        const val DATABASE_NAME = "eternamente.db"

        /**
         * Migración v1 → v2:
         * - Añade `email TEXT NOT NULL DEFAULT ''` a la tabla `users`.
         * - Crea la tabla `user_credentials` para auth local con PIN.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Añadir columna email a usuarios existentes (DEFAULT '' para compatibilidad)
                db.execSQL("ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT ''")

                // Tabla de credenciales de autenticación local
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_credentials (
                        userId TEXT NOT NULL PRIMARY KEY,
                        pinHash TEXT NOT NULL,
                        pinSalt TEXT NOT NULL,
                        failedLoginAttempts INTEGER NOT NULL DEFAULT 0,
                        lockedUntil INTEGER,
                        FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
