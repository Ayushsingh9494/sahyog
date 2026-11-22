package com.sahyog.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoPointTest {

    @Test
    fun `distance between two known cities`() {
        val jalandhar = GeoPoint(31.3260, 75.5762)
        val amritsar = GeoPoint(31.6340, 74.8723)
        // About 75 km apart in a straight line.
        assertEquals(75.0, jalandhar.distanceKmTo(amritsar), 2.0)
    }

    @Test
    fun `distance is symmetric and zero to itself`() {
        val a = GeoPoint(31.3085, 75.5871)
        val b = GeoPoint(31.2560, 75.7050)
        assertEquals(a.distanceKmTo(b), b.distanceKmTo(a), 1e-9)
        assertEquals(0.0, a.distanceKmTo(a), 1e-9)
    }

    @Test
    fun `coarsened keeps about one kilometre of precision`() {
        val precise = GeoPoint(31.256789, 75.704321)
        assertEquals(GeoPoint(31.26, 75.70), precise.coarsened())
        assertEquals(0.0, precise.distanceKmTo(precise.coarsened()), 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects impossible coordinates`() {
        GeoPoint(91.0, 0.0)
    }
}
