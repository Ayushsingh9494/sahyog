package com.sahyog.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.Priority
import com.sahyog.core.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** The runtime permissions the app requests together; the user may grant only approximate. */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

/** Outcome of asking the device where it is. */
sealed interface DeviceLocationResult {
    data class Found(val point: GeoPoint) : DeviceLocationResult

    /** The app doesn't hold a location permission. */
    data object PermissionDenied : DeviceLocationResult

    /** Location is switched off in system settings. */
    data object LocationOff : DeviceLocationResult

    /** Permission and settings are fine but no fix arrived in time (e.g. indoors, no network). */
    data object Unavailable : DeviceLocationResult
}

/** One-shot current location. Never tracks in the background. */
interface DeviceLocationProvider {
    fun hasPermission(): Boolean

    suspend fun currentLocation(): DeviceLocationResult
}

internal class FusedDeviceLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: FusedLocationProviderClient,
) : DeviceLocationProvider {

    override fun hasPermission(): Boolean = LOCATION_PERMISSIONS.any {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    // Permission is checked just above each call; lint can't follow that through hasPermission().
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): DeviceLocationResult {
        if (!hasPermission()) return DeviceLocationResult.PermissionDenied
        if (!isLocationEnabled()) return DeviceLocationResult.LocationOff

        val request = CurrentLocationRequest.Builder()
            // Network-level accuracy is plenty for distances to workers and saves battery.
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setMaxUpdateAgeMillis(MAX_AGE_MS)
            .build()
        val location = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { client.getCurrentLocation(request, null).await() }.getOrNull()
        } ?: runCatching { client.lastLocation.await() }.getOrNull()

        return location?.let { DeviceLocationResult.Found(GeoPoint(it.latitude, it.longitude)) }
            ?: DeviceLocationResult.Unavailable
    }

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            // Android 8.x has no single switch: location is "on" if any provider is.
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L

        /** A fix up to 5 minutes old is fine: people don't move far between screens. */
        private const val MAX_AGE_MS = 5 * 60_000L
    }
}
