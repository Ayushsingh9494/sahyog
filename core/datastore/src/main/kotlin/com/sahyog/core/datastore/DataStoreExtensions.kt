package com.sahyog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/** Data that falls back to empty (defaults) if the file is unreadable, instead of crashing. */
internal val DataStore<Preferences>.safeData: Flow<Preferences>
    get() = data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

/** Unknown values (e.g. written by an older app version) map to null instead of crashing. */
internal inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
