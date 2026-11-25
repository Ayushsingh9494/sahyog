package com.sahyog.core.location.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.sahyog.core.location.AddressLookup
import com.sahyog.core.location.AndroidAddressLookup
import com.sahyog.core.location.DeviceLocationProvider
import com.sahyog.core.location.FusedDeviceLocationProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface LocationModule {

    @Binds
    fun bindsDeviceLocationProvider(impl: FusedDeviceLocationProvider): DeviceLocationProvider

    @Binds
    fun bindsAddressLookup(impl: AndroidAddressLookup): AddressLookup

    companion object {
        @Provides
        fun providesFusedLocationClient(@ApplicationContext context: Context): FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)
    }
}
