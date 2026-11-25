package com.sahyog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sahyog.core.datastore.di.LocalProfilesStore
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.WorkerVerificationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Profile data the server owns: everything about a user except their identity. */
data class StoredProfile(
    val name: String,
    val roles: Set<UserRole>,
    val workerStatus: WorkerVerificationStatus?,
)

/**
 * On-device copy of the signed-in user's profile from the Sahyog server, keyed by Firebase
 * uid, so the app opens straight into the right section (even offline) without waiting for
 * the network. The server stays the source of truth; this is cleared on sign-out.
 */
@Singleton
class LocalProfileDataSource @Inject constructor(
    @param:LocalProfilesStore private val dataStore: DataStore<Preferences>,
) {
    fun profile(uid: String): Flow<StoredProfile?> = dataStore.safeData
        .map { prefs ->
            val keys = Keys(uid)
            val name = prefs[keys.name] ?: return@map null
            StoredProfile(
                name = name,
                roles = prefs[keys.roles].orEmpty().mapNotNull { it.toEnumOrNull<UserRole>() }.toSet(),
                workerStatus = prefs[keys.workerStatus].toEnumOrNull<WorkerVerificationStatus>(),
            )
        }
        .distinctUntilChanged()

    suspend fun save(uid: String, profile: StoredProfile) {
        val keys = Keys(uid)
        dataStore.edit { prefs ->
            prefs[keys.name] = profile.name
            prefs[keys.roles] = profile.roles.map { it.name }.toSet()
            val status = profile.workerStatus
            if (status == null) prefs.remove(keys.workerStatus) else prefs[keys.workerStatus] = status.name
        }
    }

    /** The cooperative's verification decision for a worker, as last reported by the server. */
    suspend fun updateWorkerStatus(uid: String, status: WorkerVerificationStatus) {
        val keys = Keys(uid)
        dataStore.edit { prefs ->
            if (prefs[keys.name] != null) prefs[keys.workerStatus] = status.name
        }
    }

    suspend fun remove(uid: String) {
        val keys = Keys(uid)
        dataStore.edit { prefs ->
            prefs.remove(keys.name)
            prefs.remove(keys.roles)
            prefs.remove(keys.workerStatus)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private class Keys(uid: String) {
        val name = stringPreferencesKey("$uid.name")
        val roles = stringSetPreferencesKey("$uid.roles")
        val workerStatus = stringPreferencesKey("$uid.worker_status")
    }
}
