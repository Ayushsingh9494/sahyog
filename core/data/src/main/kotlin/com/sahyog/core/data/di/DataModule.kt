package com.sahyog.core.data.di

import com.google.firebase.auth.FirebaseAuth
import com.sahyog.core.data.auth.FirebaseAuthRepository
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.LocalProfileUserRepository
import com.sahyog.core.data.repository.OfflineUserPreferencesRepository
import com.sahyog.core.data.repository.UserPreferencesRepository
import com.sahyog.core.data.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Which implementation backs each repository. The only place that changes when an
 * implementation is swapped (e.g. LocalProfileUserRepository -> API-backed in Milestone 10).
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

    @Binds
    fun bindsAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    fun bindsUserRepository(impl: LocalProfileUserRepository): UserRepository

    @Binds
    fun bindsUserPreferencesRepository(impl: OfflineUserPreferencesRepository): UserPreferencesRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object FirebaseModule {

    @Provides
    @Singleton
    fun providesFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        // SMS and reCAPTCHA screens follow the device language (Hindi users get Hindi SMS).
        useAppLanguage()
    }
}
