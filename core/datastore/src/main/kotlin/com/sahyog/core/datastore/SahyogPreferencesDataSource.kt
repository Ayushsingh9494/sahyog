package com.sahyog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sahyog.core.datastore.di.UserPreferencesStore
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.UserPreferences
import com.sahyog.core.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local UI preferences (Jetpack DataStore). Holds no personal data;
 * identity is managed by Firebase Auth, and profiles by the backend.
 */
@Singleton
class SahyogPreferencesDataSource @Inject constructor(
    @param:UserPreferencesStore private val dataStore: DataStore<Preferences>,
) {
    val userPreferences: Flow<UserPreferences> = dataStore.safeData
        .map { prefs ->
            UserPreferences(
                theme = prefs[Keys.theme].toEnumOrNull<ThemePreference>() ?: ThemePreference.System,
                activeRole = prefs[Keys.activeRole].toEnumOrNull<UserRole>(),
            )
        }
        .distinctUntilChanged()

    suspend fun setTheme(theme: ThemePreference) {
        dataStore.edit { it[Keys.theme] = theme.name }
    }

    suspend fun setActiveRole(role: UserRole?) {
        dataStore.edit { prefs ->
            if (role == null) prefs.remove(Keys.activeRole) else prefs[Keys.activeRole] = role.name
        }
    }

    /** Last known device location, already coarsened (~1 km). Never leaves the device. */
    val lastLocation: Flow<GeoPoint?> = dataStore.safeData
        .map { prefs ->
            val lat = prefs[Keys.latitude] ?: return@map null
            val lng = prefs[Keys.longitude] ?: return@map null
            runCatching { GeoPoint(lat, lng) }.getOrNull()
        }
        .distinctUntilChanged()

    suspend fun setLastLocation(point: GeoPoint?) {
        dataStore.edit { prefs ->
            if (point == null) {
                prefs.remove(Keys.latitude)
                prefs.remove(Keys.longitude)
            } else {
                prefs[Keys.latitude] = point.latitude
                prefs[Keys.longitude] = point.longitude
            }
        }
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val activeRole = stringPreferencesKey("active_role")
        val latitude = doublePreferencesKey("last_latitude")
        val longitude = doublePreferencesKey("last_longitude")
    }
}
