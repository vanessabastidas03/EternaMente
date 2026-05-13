package com.eternamente.app.di

import android.content.Context
import com.eternamente.app.data.local.crypto.CryptoManager
import com.eternamente.app.data.local.database.EternaDatabase
import com.eternamente.app.data.local.database.dao.BaselineDao
import com.eternamente.app.data.local.database.dao.GameResultDao
import com.eternamente.app.data.local.database.dao.GamificationDao
import com.eternamente.app.data.local.database.dao.MlPredictionDao
import com.eternamente.app.data.local.database.dao.SessionDao
import com.eternamente.app.data.local.database.dao.SettingsDao
import com.eternamente.app.data.local.database.dao.UserCredentialsDao
import com.eternamente.app.data.local.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
        val key = cryptoManager.getOrCreateDatabaseKey()
        val db  = EternaDatabase.create(context, key)
        key.fill(0)
        return db
    }

    @Provides fun provideUserDao(db: EternaDatabase): UserDao = db.userDao()
    @Provides fun provideUserCredentialsDao(db: EternaDatabase): UserCredentialsDao = db.userCredentialsDao()
    @Provides fun provideSessionDao(db: EternaDatabase): SessionDao = db.sessionDao()
    @Provides fun provideGameResultDao(db: EternaDatabase): GameResultDao = db.gameResultDao()
    @Provides fun provideBaselineDao(db: EternaDatabase): BaselineDao = db.baselineDao()
    @Provides fun provideMlPredictionDao(db: EternaDatabase): MlPredictionDao = db.mlPredictionDao()
    @Provides fun provideGamificationDao(db: EternaDatabase): GamificationDao = db.gamificationDao()
    @Provides fun provideSettingsDao(db: EternaDatabase): SettingsDao = db.settingsDao()
}
