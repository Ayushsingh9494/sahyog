package com.sahyog.core.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on Earth in degrees (WGS84, as used by GPS and Google Maps). */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }

    /** Straight-line ("as the crow flies") distance in kilometres, by the haversine formula. */
    fun distanceKmTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    /**
     * About 1 km precision. Used whenever a location leaves the device or is stored, so
     * nobody can pinpoint a home from it.
     */
    fun coarsened(): GeoPoint = GeoPoint(latitude.roundTo(COARSE_DECIMALS), longitude.roundTo(COARSE_DECIMALS))

    /**
     * About 1 m precision: enough for a worker to find the door, without pretending to
     * GPS accuracy the phone doesn't have. Only for a customer's own booking address.
     */
    fun doorstep(): GeoPoint = GeoPoint(latitude.roundTo(DOORSTEP_DECIMALS), longitude.roundTo(DOORSTEP_DECIMALS))

    private fun Double.roundTo(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return kotlin.math.round(this * factor) / factor
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0088
        private const val COARSE_DECIMALS = 2
        private const val DOORSTEP_DECIMALS = 5

        /** A point from optional stored/received coordinates; null if missing or out of range. */
        fun ofOrNull(latitude: Double?, longitude: Double?): GeoPoint? {
            if (latitude == null || longitude == null) return null
            return runCatching { GeoPoint(latitude, longitude) }.getOrNull()
        }

        /** Radius choices offered to customers, in km. */
        val RADIUS_OPTIONS_KM = listOf(2, 5, 10, 25)
    }
}
