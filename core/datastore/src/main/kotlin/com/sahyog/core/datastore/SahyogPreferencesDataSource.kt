package com.sahyog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserPreferences
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.WorkerVerificationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local key/value storage (Jetpack DataStore).
 *
 * Holds UI preferences and a cached copy of the signed-in user's profile, so the
 * app can decide where to start instantly on launch, even offline.
 * Auth tokens are NOT stored here; Firebase manages them securely.
 */
@Singleton
class SahyogPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val data: Flow<Preferences> = dataStore.data.catch { error ->
        // A corrupt/unreadable file must not crash the app: fall back to defaults.
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val userPreferences: Flow<UserPreferences> = data
        .map { prefs ->
            UserPreferences(
                theme = prefs[Keys.theme].toEnumOrNull<ThemePreference>() ?: ThemePreference.System,
                activeRole = prefs[Keys.activeRole].toEnumOrNull<UserRole>(),
            )
        }
        .distinctUntilChanged()

    val cachedUser: Flow<User?> = data
        .map { prefs ->
            val id = prefs[Keys.userId] ?: return@map null
            val phone = prefs[Keys.userPhone] ?: return@map null
            User(
                id = id,
                phoneNumber = phone,
                name = prefs[Keys.userName],
                roles = prefs[Keys.userRoles].orEmpty().mapNotNull { it.toEnumOrNull<UserRole>() }.toSet(),
                workerStatus = prefs[Keys.workerStatus].toEnumOrNull<WorkerVerificationStatus>(),
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

    suspend fun saveUser(user: User) {
        dataStore.edit { prefs ->
            prefs[Keys.userId] = user.id
            prefs[Keys.userPhone] = user.phoneNumber
            val name = user.name
            if (name == null) prefs.remove(Keys.userName) else prefs[Keys.userName] = name
            prefs[Keys.userRoles] = user.roles.map { it.name }.toSet()
            val status = user.workerStatus
            if (status == null) prefs.remove(Keys.workerStatus) else prefs[Keys.workerStatus] = status.name
        }
    }

    /** Removes the cached profile and the remembered mode. Theme is a device setting and is kept. */
    suspend fun clearSession() {
        dataStore.edit { prefs ->
            listOf(Keys.userId, Keys.userPhone, Keys.userName, Keys.workerStatus, Keys.activeRole)
                .forEach { prefs.remove(it) }
            prefs.remove(Keys.userRoles)
        }
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val activeRole = stringPreferencesKey("active_role")
        val userId = stringPreferencesKey("user_id")
        val userPhone = stringPreferencesKey("user_phone")
        val userName = stringPreferencesKey("user_name")
        val userRoles = stringSetPreferencesKey("user_roles")
        val workerStatus = stringPreferencesKey("worker_status")
    }
}

// Unknown values (e.g. from an older app version) map to null instead of crashing.
private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
