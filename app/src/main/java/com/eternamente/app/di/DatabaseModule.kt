package com.eternamente.app.di

import android.content.Context
import androidx.room.Room
import com.eternamente.app.data.local.crypto.CryptoManager
import com.eternamente.app.data.local.db.EternaDatabase
import com.eternamente.app.data.local.db.dao.GameResultDao
import com.eternamente.app.data.local.db.dao.GamificationDao
import com.eternamente.app.data.local.db.dao.MlPredictionDao
import com.eternamente.app.data.local.db.dao.SessionDao
import com.eternamente.app.data.local.db.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

/**
 * Hilt module for the local encrypted database and its DAOs.
 *
 * **SQLCipher setup**:
 * 1. [CryptoManager.getOrCreateDatabaseKey] returns a fresh 32-byte passphrase
 *    on first launch or the decrypted passphrase on subsequent launches.
 * 2. A [SupportFactory] wraps the passphrase and is passed to Room's builder.
 * 3. The raw [ByteArray] is **zeroed immediately** after [SupportFactory] copies it,
 *    minimising the time the plaintext key is live on the heap.
 *
 * DAOs are provided as unscoped (new instance per injection site) because they are
 * lightweight wrappers around the singleton [EternaDatabase].
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideEternaDatabase(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager
    ): EternaDatabase {
        val passphrase: ByteArray = cryptoManager.getOrCreateDatabaseKey()
        val factory               = SupportFactory(passphrase)
        passphrase.fill(0)        // Zero the plaintext key from the heap immediately

        return Room.databaseBuilder(
            context,
            EternaDatabase::class.java,
            EternaDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)
            // Production: replace with explicit MigrationStrategy when bumping version
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideUserDao(db: EternaDatabase): UserDao = db.userDao()

    @Provides
    fun provideSessionDao(db: EternaDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideGameResultDao(db: EternaDatabase): GameResultDao = db.gameResultDao()

    @Provides
    fun provideMlPredictionDao(db: EternaDatabase): MlPredictionDao = db.mlPredictionDao()

    @Provides
    fun provideGamificationDao(db: EternaDatabase): GamificationDao = db.gamificationDao()
}
