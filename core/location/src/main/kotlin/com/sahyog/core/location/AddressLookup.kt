package com.sahyog.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.sahyog.core.common.dispatcher.Dispatcher
import com.sahyog.core.common.dispatcher.SahyogDispatcher
import com.sahyog.core.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Turns a map point into a readable address ("reverse geocoding"), to pre-fill the
 * booking address after the customer drops a pin. Best effort: null when the device
 * has no geocoder, is offline, or nothing is known there; the customer types it instead.
 */
interface AddressLookup {
    suspend fun addressAt(point: GeoPoint): String?
}

/**
 * Uses Android's built-in [Geocoder] (Google Play services on most phones). It needs no
 * API key and costs nothing, unlike the Geocoding web API.
 */
internal class AndroidAddressLookup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(SahyogDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AddressLookup {

    override suspend fun addressAt(point: GeoPoint): String? {
        if (!Geocoder.isPresent()) return null
        // The app's language, so Hindi users get Hindi place names where available.
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = withTimeoutOrNull(TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        point.latitude,
                        point.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                continuation.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                continuation.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(ioDispatcher) {
                    try {
                        @Suppress("DEPRECATION") // The listener version needs API 33.
                        geocoder.getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
                    } catch (e: IOException) {
                        null // Offline or the service is unavailable.
                    }
                }
            }
        }
        return address?.format()
    }

    private fun Address.format(): String? =
        getAddressLine(0)?.trim()?.ifBlank { null }
            ?: listOfNotNull(subLocality, locality, adminArea).joinToString(", ").ifBlank { null }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
