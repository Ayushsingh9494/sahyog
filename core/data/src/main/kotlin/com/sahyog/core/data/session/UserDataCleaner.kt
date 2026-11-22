package com.sahyog.core.data.session

/**
 * Anything that caches the signed-in person's data on the device. Every implementation
 * is bound into a set (`@IntoSet`) and cleared on sign-out, so the next person to use
 * the phone never sees the previous person's bookings, addresses, etc.
 */
interface UserDataCleaner {
    suspend fun clearUserData()
}
