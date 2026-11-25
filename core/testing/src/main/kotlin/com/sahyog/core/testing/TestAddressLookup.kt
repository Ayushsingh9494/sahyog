package com.sahyog.core.testing

import com.sahyog.core.location.AddressLookup
import com.sahyog.core.model.GeoPoint

/** Answers from [addresses]; unknown points have no address. Records what was asked. */
class TestAddressLookup(var addresses: Map<GeoPoint, String> = emptyMap()) : AddressLookup {
    val lookedUp = mutableListOf<GeoPoint>()

    override suspend fun addressAt(point: GeoPoint): String? {
        lookedUp += point
        return addresses[point]
    }
}
