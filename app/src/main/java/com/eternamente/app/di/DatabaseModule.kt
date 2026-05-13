package com.eternamente.app.di

import android.content.Context
import androidx.room.Room
import com.eternamente.app.data.local.crypto.CryptoManager
import com.eternamente.app.data.local.db.EternaDatabase
import com.eternamente.app.data.local.db.dao.GameResultDao
import com.eternamente.app.data.local.db.dao.GamificationDao
import com.eternamente.app.data.local.db.dao.MlPredictionDao
import com.eternamente.app.data.local.db.dao.SessionDao
import com.eternamente.app.data.local.db.dao.UserCredentialsDao
import com.eternamente.app.data.local.db.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

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
        passphrase.fill(0)

        return Room.databaseBuilder(
            context,
            EternaDatabase::class.java,
            EternaDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)
            .addMigrations(EternaDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides fun provideUserDao(db: EternaDatabase): UserDao = db.userDao()
    @Provides fun provideSessionDao(db: EternaDatabase): SessionDao = db.sessionDao()
    @Provides fun provideGameResultDao(db: EternaDatabase): GameResultDao = db.gameResultDao()
    @Provides fun provideMlPredictionDao(db: EternaDatabase): MlPredictionDao = db.mlPredictionDao()
    @Provides fun provideGamificationDao(db: EternaDatabase): GamificationDao = db.gamificationDao()
    @Provides fun provideUserCredentialsDao(db: EternaDatabase): UserCredentialsDao = db.userCredentialsDao()
}
