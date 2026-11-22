package com.sahyog.core.common.time

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Code that depends on "now" (bookable slots, booking timestamps) takes a [Clock]
 * instead of calling `LocalDateTime.now()`, so tests can pin the time.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    // Not a singleton: the system default zone is read each time, so a timezone
    // change on the device takes effect without restarting the app.
    @Provides
    fun providesClock(): Clock = Clock.systemDefaultZone()
}
