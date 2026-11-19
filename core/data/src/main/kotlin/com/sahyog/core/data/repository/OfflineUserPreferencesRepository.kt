package com.sahyog.core.data.repository

import com.sahyog.core.datastore.SahyogPreferencesDataSource
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.UserPreferences
import com.sahyog.core.model.UserRole
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class OfflineUserPreferencesRepository @Inject constructor(
    private val local: SahyogPreferencesDataSource,
) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = local.userPreferences

    override suspend fun setTheme(theme: ThemePreference) = local.setTheme(theme)

    override suspend fun setActiveRole(role: UserRole) = local.setActiveRole(role)
}
