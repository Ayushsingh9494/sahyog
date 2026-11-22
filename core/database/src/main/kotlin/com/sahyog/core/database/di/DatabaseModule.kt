package com.sahyog.core.database.di

import android.content.Context
import androidx.room.Room
import com.sahyog.core.database.SahyogDatabase
import com.sahyog.core.database.dao.BookingDao
import com.sahyog.core.database.dao.CatalogDao
import com.sahyog.core.database.dao.JobDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): SahyogDatabase =
        Room.databaseBuilder(context, SahyogDatabase::class.java, "sahyog.db").build()

    @Provides
    fun providesCatalogDao(database: SahyogDatabase): CatalogDao = database.catalogDao()

    @Provides
    fun providesBookingDao(database: SahyogDatabase): BookingDao = database.bookingDao()

    @Provides
    fun providesJobDao(database: SahyogDatabase): JobDao = database.jobDao()
}
