package com.sahyog.core.testing

import com.sahyog.core.data.repository.LocationRepository
import com.sahyog.core.location.DeviceLocationResult
import com.sahyog.core.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Controllable location: set the permission and what the "device" reports next. */
class TestLocationRepository : LocationRepository {
    private val saved = MutableStateFlow<GeoPoint?>(null)
    override val location: Flow<GeoPoint?> = saved

    var permissionGranted = false
    var nextResult: DeviceLocationResult = DeviceLocationResult.Unavailable
    var updateCount = 0
        private set

    fun setLocation(point: GeoPoint?) {
        saved.value = point
    }

    override fun hasPermission(): Boolean = permissionGranted

    override suspend fun updateFromDevice(): DeviceLocationResult {
        updateCount++
        val result = nextResult
        if (result is DeviceLocationResult.Found) saved.value = result.point.coarsened()
        return result
    }
}

/** Points used by location tests: Model Town (Jalandhar) and nearby places. */
object TestPlaces {
    val modelTown = GeoPoint(31.3085, 75.5871)
    val civilLines = GeoPoint(31.3302, 75.5796)
    /** About 75 km away. */
    val amritsar = GeoPoint(31.6340, 74.8723)
}
