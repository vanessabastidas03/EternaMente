package com.eternamente.app.data.local.database

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.eternamente.app.data.local.database.dao.BaselineDao
import com.eternamente.app.data.local.database.dao.GameResultDao
import com.eternamente.app.data.local.database.dao.GamificationDao
import com.eternamente.app.data.local.database.dao.MlPredictionDao
import com.eternamente.app.data.local.database.dao.SessionDao
import com.eternamente.app.data.local.database.dao.SettingsDao
import com.eternamente.app.data.local.database.dao.UserCredentialsDao
import com.eternamente.app.data.local.database.dao.UserDao
import com.eternamente.app.data.local.database.entity.BaselineEntity
import com.eternamente.app.data.local.database.entity.GameResultEntity
import com.eternamente.app.data.local.database.entity.GamificationEntity
import com.eternamente.app.data.local.database.entity.MlPredictionEntity
import com.eternamente.app.data.local.database.entity.SessionEntity
import com.eternamente.app.data.local.database.entity.SettingsEntity
import com.eternamente.app.data.local.database.entity.UserCredentialsEntity
import com.eternamente.app.data.local.database.entity.UserEntity
import net.sqlcipher.database.SupportFactory

/**
 * Base de datos Room cifrada con SQLCipher (AES-256-CBC).
 *
 * ## Entidades y relaciones
 * ```
 * users ─────────────────┬── cognitive_sessions ── game_results
 *   │                    ├── cognitive_baselines
 *   │                    ├── ml_predictions
 *   │                    ├── gamification
 *   │                    ├── user_settings
 *   └── user_credentials └── (user_credentials eliminadas en cascada con users)
 * ```
 *
 * ## TypeConverters aplicados ([Converters]):
 * - [java.time.LocalDate] ↔ Long (epochDay)
 * - [List]<[com.eternamente.app.domain.model.CognitiveDomain]> ↔ String
 * - [List]<[com.eternamente.app.domain.model.Badge]> ↔ String
 * - [com.eternamente.app.domain.model.SessionType] ↔ String
 * - [com.eternamente.app.domain.model.AlertLevel] ↔ String
 *
 * ## Creación
 * - Producción: [EternaDatabase.create] — pasa la clave de [com.eternamente.app.data.local.crypto.CryptoManager].
 * - Tests: [EternaDatabase.createInMemory] — sin cifrado, sin archivos en disco.
 *
 * ## Schema export
 * Los archivos JSON de schema se exportan a `app/schemas/` para mantener historial
 * de migraciones revisable por control de versiones.
 */
@Database(
    entities = [
        UserEntity::class,
        UserCredentialsEntity::class,
        SessionEntity::class,
        GameResultEntity::class,
        BaselineEntity::class,
        MlPredictionEntity::class,
        GamificationEntity::class,
        SettingsEntity::class
    ],
    version      = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class EternaDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun userCredentialsDao(): UserCredentialsDao
    abstract fun sessionDao(): SessionDao
    abstract fun gameResultDao(): GameResultDao
    abstract fun baselineDao(): BaselineDao
    abstract fun mlPredictionDao(): MlPredictionDao
    abstract fun gamificationDao(): GamificationDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DATABASE_NAME = "eternamente.db"

        /**
         * Crea la base de datos cifrada con SQLCipher.
         *
         * La clave debe provenir de [com.eternamente.app.data.local.crypto.CryptoManager]
         * y **zeroearse inmediatamente** después de esta llamada:
         * ```kotlin
         * val key = cryptoManager.getOrCreateDatabaseKey()
         * val db  = EternaDatabase.create(context, key)
         * key.fill(0)   // ← obligatorio
         * ```
         *
         * @param context       Application context.
         * @param encryptionKey Clave AES-256 de 32 bytes obtenida del Android Keystore.
         */
        fun create(context: Context, encryptionKey: ByteArray): EternaDatabase =
            Room.databaseBuilder(context, EternaDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(SupportFactory(encryptionKey))
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

        /**
         * Crea una instancia en memoria **sin cifrado** para tests de integración.
         *
         * - Sin SQLCipher (evita el overhead de inicialización de librerías nativas en JVM).
         * - [allowMainThreadQueries] habilitado para simplificar tests con `runBlocking`.
         * - Los datos no persisten entre test cases.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun createInMemory(context: Context): EternaDatabase =
            Room.inMemoryDatabaseBuilder(context, EternaDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
