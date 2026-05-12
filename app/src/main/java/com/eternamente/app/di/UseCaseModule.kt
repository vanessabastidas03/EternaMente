package com.eternamente.app.di

import com.eternamente.app.domain.repository.GameResultRepository
import com.eternamente.app.domain.repository.GamificationRepository
import com.eternamente.app.domain.repository.MlRepository
import com.eternamente.app.domain.repository.SessionRepository
import com.eternamente.app.domain.repository.UserRepository
import com.eternamente.app.domain.usecase.AnalyzeCognitivePatternUseCase
import com.eternamente.app.domain.usecase.GenerateReportUseCase
import com.eternamente.app.domain.usecase.LoginUserUseCase
import com.eternamente.app.domain.usecase.RegisterUserUseCase
import com.eternamente.app.domain.usecase.SaveGameResultUseCase
import com.eternamente.app.domain.usecase.StartSessionUseCase
import com.eternamente.app.domain.usecase.UpdateGamificationUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Hilt module that provides domain use cases into the [ViewModelComponent].
 *
 * **Why [ViewModelComponent]?**
 * Use cases are scoped to the ViewModel lifecycle rather than the Application
 * singleton, so a fresh instance is provided to each ViewModel. This avoids
 * accidental state sharing across screens and aligns with Clean Architecture
 * principles where use cases are stateless orchestrators.
 *
 * Repositories injected here are singletons from [SingletonComponent], which is
 * a parent of [ViewModelComponent] in Hilt's component hierarchy — so they
 * are always accessible from this scope.
 *
 * No `@ViewModelScoped` annotation: use cases are stateless, so there is no
 * benefit to caching them within a single ViewModel instance.
 */
@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    /**
     * Provides [RegisterUserUseCase] — orchestrates user creation + consent
     * recording + gamification profile initialisation.
     */
    @Provides
    fun provideRegisterUserUseCase(
        userRepository: UserRepository,
        gamificationRepository: GamificationRepository
    ): RegisterUserUseCase = RegisterUserUseCase(userRepository, gamificationRepository)

    /**
     * Provides [LoginUserUseCase] — authenticates via Firebase Auth and loads
     * the local user profile.
     */
    @Provides
    fun provideLoginUserUseCase(
        userRepository: UserRepository
    ): LoginUserUseCase = LoginUserUseCase(userRepository)

    /**
     * Provides [StartSessionUseCase] — validates business rules and creates a
     * new [com.eternamente.app.domain.model.CognitiveSession].
     */
    @Provides
    fun provideStartSessionUseCase(
        sessionRepository: SessionRepository
    ): StartSessionUseCase = StartSessionUseCase(sessionRepository)

    /**
     * Provides [SaveGameResultUseCase] — validates and persists a single
     * [com.eternamente.app.domain.model.GameResult].
     */
    @Provides
    fun provideSaveGameResultUseCase(
        gameResultRepository: GameResultRepository
    ): SaveGameResultUseCase = SaveGameResultUseCase(gameResultRepository)

    /**
     * Provides [AnalyzeCognitivePatternUseCase] — triggers on-device ML inference
     * and returns the resulting [com.eternamente.app.domain.model.MlPrediction].
     */
    @Provides
    fun provideAnalyzeCognitivePatternUseCase(
        gameResultRepository: GameResultRepository,
        mlRepository: MlRepository
    ): AnalyzeCognitivePatternUseCase =
        AnalyzeCognitivePatternUseCase(gameResultRepository, mlRepository)

    /**
     * Provides [GenerateReportUseCase] — aggregates user profile, recent results,
     * baseline and latest prediction into a single [GenerateReportUseCase.CognitiveReport].
     */
    @Provides
    fun provideGenerateReportUseCase(
        userRepository: UserRepository,
        gameResultRepository: GameResultRepository,
        mlRepository: MlRepository
    ): GenerateReportUseCase =
        GenerateReportUseCase(userRepository, gameResultRepository, mlRepository)

    /**
     * Provides [UpdateGamificationUseCase] — awards points, updates streaks and
     * unlocks badges after a session completes.
     */
    @Provides
    fun provideUpdateGamificationUseCase(
        gamificationRepository: GamificationRepository
    ): UpdateGamificationUseCase = UpdateGamificationUseCase(gamificationRepository)
}
