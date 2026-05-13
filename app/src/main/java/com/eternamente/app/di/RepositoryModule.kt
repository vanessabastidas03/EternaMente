package com.eternamente.app.di

import com.eternamente.app.data.repository.AuthRepositoryImpl
import com.eternamente.app.data.repository.BaselineRepositoryImpl
import com.eternamente.app.data.repository.FeatureQueryRepositoryImpl
import com.eternamente.app.data.repository.GameResultRepositoryImpl
import com.eternamente.app.data.repository.GamificationRepositoryImpl
import com.eternamente.app.data.repository.MlRepositoryImpl
import com.eternamente.app.data.repository.SessionRepositoryImpl
import com.eternamente.app.data.repository.UserRepositoryImpl
import com.eternamente.app.domain.repository.AuthRepository
import com.eternamente.app.domain.repository.BaselineRepository
import com.eternamente.app.domain.repository.FeatureQueryRepository
import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.GamificationRepository
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
    @Binds @Singleton abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
    @Binds @Singleton abstract fun bindGameResultRepository(impl: GameResultRepositoryImpl): GameResultRepository
    @Binds @Singleton abstract fun bindMlRepository(impl: MlRepositoryImpl): MlRepository
    @Binds @Singleton abstract fun bindGamificationRepository(impl: GamificationRepositoryImpl): GamificationRepository
    @Binds @Singleton abstract fun bindBaselineRepository(impl: BaselineRepositoryImpl): BaselineRepository
    @Binds @Singleton abstract fun bindFeatureQueryRepository(impl: FeatureQueryRepositoryImpl): FeatureQueryRepository
}
