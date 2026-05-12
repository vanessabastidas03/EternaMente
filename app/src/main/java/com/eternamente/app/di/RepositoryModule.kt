package com.eternamente.app.di

import com.eternamente.app.data.repository.GameResultRepositoryImpl
import com.eternamente.app.data.repository.GamificationRepositoryImpl
import com.eternamente.app.data.repository.MlRepositoryImpl
import com.eternamente.app.data.repository.SessionRepositoryImpl
import com.eternamente.app.data.repository.UserRepositoryImpl
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

/**
 * Hilt module that binds domain repository interfaces to their data-layer implementations.
 *
 * Using `@Binds` instead of `@Provides` avoids creating wrapper functions and
 * allows Hilt to perform the binding at compile time with zero runtime overhead.
 *
 * All bindings are `@Singleton` — repositories hold long-lived resources
 * (DB connections, auth listeners) that must not be recreated per ViewModel.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds [UserRepositoryImpl] as the [UserRepository] throughout the app.
     *
     * [UserRepositoryImpl] is `@Singleton` on its own class declaration;
     * this `@Binds` declaration exposes that singleton under the interface type.
     */
    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    /** Binds [SessionRepositoryImpl] as the [SessionRepository]. */
    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    /** Binds [GameResultRepositoryImpl] as the [GameResultRepository]. */
    @Binds
    @Singleton
    abstract fun bindGameResultRepository(impl: GameResultRepositoryImpl): GameResultRepository

    /** Binds [MlRepositoryImpl] as the [MlRepository]. */
    @Binds
    @Singleton
    abstract fun bindMlRepository(impl: MlRepositoryImpl): MlRepository

    /** Binds [GamificationRepositoryImpl] as the [GamificationRepository]. */
    @Binds
    @Singleton
    abstract fun bindGamificationRepository(impl: GamificationRepositoryImpl): GamificationRepository
}
