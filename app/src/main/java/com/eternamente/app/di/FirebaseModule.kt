package com.eternamente.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that exposes Firebase SDK singletons to the dependency graph.
 *
 * Both [FirebaseAuth] and [FirebaseMessaging] are themselves singletons within
 * the Firebase SDK; wrapping them here ensures they are also treated as
 * singletons within Hilt's [SingletonComponent].
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Provides the [FirebaseAuth] instance for authentication operations.
     *
     * Consumed by [com.eternamente.app.data.repository.UserRepositoryImpl].
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Provides the [FirebaseMessaging] instance for push-notification token management.
     *
     * Consumed by WorkManager tasks that register/refresh FCM tokens.
     */
    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
