package com.sahyog.core.data.di

import com.google.firebase.auth.FirebaseAuth
import com.sahyog.core.data.auth.FirebaseAuthRepository
import com.sahyog.core.data.booking.BookingNetworkDataSource
import com.sahyog.core.data.booking.DemoBookingDataSource
import com.sahyog.core.data.booking.OfflineFirstBookingRepository
import com.sahyog.core.data.catalog.CatalogNetworkDataSource
import com.sahyog.core.data.catalog.DemoCatalogDataSource
import com.sahyog.core.data.catalog.OfflineFirstCatalogRepository
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.BookingRepository
import com.sahyog.core.data.repository.LocalProfileUserRepository
import com.sahyog.core.data.repository.OfflineUserPreferencesRepository
import com.sahyog.core.data.repository.UserPreferencesRepository
import com.sahyog.core.data.repository.UserRepository
import com.sahyog.core.data.session.UserDataCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
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

    @Binds
    fun bindsCatalogRepository(impl: OfflineFirstCatalogRepository): CatalogRepository

    /** Demo data until the Spring Boot API exists (M10). */
    @Binds
    fun bindsCatalogNetworkDataSource(impl: DemoCatalogDataSource): CatalogNetworkDataSource

    @Binds
    fun bindsBookingRepository(impl: OfflineFirstBookingRepository): BookingRepository

    /** Demo server until the Spring Boot API exists (M10). */
    @Binds
    fun bindsBookingNetworkDataSource(impl: DemoBookingDataSource): BookingNetworkDataSource

    /** Cached bookings hold personal data (addresses): cleared on sign-out. */
    @Binds
    @IntoSet
    fun bindsBookingCleaner(impl: OfflineFirstBookingRepository): UserDataCleaner
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
