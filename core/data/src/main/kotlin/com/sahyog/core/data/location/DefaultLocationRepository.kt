package com.sahyog.core.data.location

import com.sahyog.core.data.repository.LocationRepository
import com.sahyog.core.data.session.UserDataCleaner
import com.sahyog.core.datastore.SahyogPreferencesDataSource
import com.sahyog.core.location.DeviceLocationProvider
import com.sahyog.core.location.DeviceLocationResult
import com.sahyog.core.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the customer is, for "near me". Only a coarsened point (~1 km) is kept, only on
 * this device, and it's wiped on sign-out. Distances are computed on the phone.
 */
@Singleton
internal class DefaultLocationRepository @Inject constructor(
    private val provider: DeviceLocationProvider,
    private val preferences: SahyogPreferencesDataSource,
) : LocationRepository, UserDataCleaner {

    override val location: Flow<GeoPoint?> = preferences.lastLocation

    override fun hasPermission(): Boolean = provider.hasPermission()

    override suspend fun updateFromDevice(): DeviceLocationResult {
        val result = provider.currentLocation()
        if (result is DeviceLocationResult.Found) preferences.setLastLocation(result.point.coarsened())
        return result
    }

    override suspend fun clearUserData() = preferences.setLastLocation(null)
}
