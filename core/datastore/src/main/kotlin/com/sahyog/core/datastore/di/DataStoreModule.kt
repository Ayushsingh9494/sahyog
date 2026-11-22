package com.sahyog.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sahyog.core.common.dispatcher.Dispatcher
import com.sahyog.core.common.dispatcher.SahyogDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPreferencesStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalProfilesStore

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {

    // Exactly one DataStore instance per file is allowed per process. @Singleton guarantees it.

    @Provides
    @Singleton
    @UserPreferencesStore
    fun providesUserPreferencesStore(
        @ApplicationContext context: Context,
        @Dispatcher(SahyogDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = create(context, ioDispatcher, "sahyog_preferences")

    @Provides
    @Singleton
    @LocalProfilesStore
    fun providesLocalProfilesStore(
        @ApplicationContext context: Context,
        @Dispatcher(SahyogDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = create(context, ioDispatcher, "sahyog_local_profiles")

    private fun create(context: Context, io: CoroutineDispatcher, name: String) =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(name) },
        )
}
