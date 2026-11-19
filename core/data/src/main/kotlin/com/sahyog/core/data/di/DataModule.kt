package com.sahyog.core.data.di

import com.sahyog.core.data.fake.FakeAuthRepository
import com.sahyog.core.data.fake.FakeUserRepository
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.OfflineUserPreferencesRepository
import com.sahyog.core.data.repository.UserPreferencesRepository
import com.sahyog.core.data.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Which implementation backs each repository. This is the only place that changes
 * when fakes are replaced by Firebase / the Spring Boot API.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

    @Binds
    fun bindsAuthRepository(impl: FakeAuthRepository): AuthRepository

    @Binds
    fun bindsUserRepository(impl: FakeUserRepository): UserRepository

    @Binds
    fun bindsUserPreferencesRepository(impl: OfflineUserPreferencesRepository): UserPreferencesRepository
}
