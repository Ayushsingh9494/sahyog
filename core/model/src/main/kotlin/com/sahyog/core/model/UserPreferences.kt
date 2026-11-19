package com.sahyog.core.model

enum class ThemePreference { System, Light, Dark }

/**
 * Device-local preferences (not synced to the backend).
 *
 * @property activeRole The mode the user last used. Only meaningful if they still hold that role.
 */
data class UserPreferences(
    val theme: ThemePreference = ThemePreference.System,
    val activeRole: UserRole? = null,
)
