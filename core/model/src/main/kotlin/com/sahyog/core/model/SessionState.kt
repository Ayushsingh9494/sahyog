package com.sahyog.core.model

/**
 * Which part of the app the user belongs in right now. The root of the UI renders
 * exactly one section per state, so changing state (login, logout, role switch)
 * swaps the whole section and discards its back stack.
 */
sealed interface SessionState {
    data object SignedOut : SessionState

    /** Signed in; waiting for the profile from the server (first sign-in on this device). */
    data object LoadingProfile : SessionState

    /** Signed in, but the profile couldn't be loaded; offer retry and sign-out. */
    data object ProfileUnavailable : SessionState

    data class NeedsOnboarding(val user: User) : SessionState

    data class SignedIn(val user: User, val activeRole: UserRole) : SessionState

    companion object {
        fun resolve(state: UserState, preferences: UserPreferences): SessionState = when (state) {
            UserState.SignedOut -> SignedOut
            UserState.Loading -> LoadingProfile
            UserState.Unavailable -> ProfileUnavailable
            is UserState.Ready -> resolve(state.user, preferences)
        }

        /**
         * Derives the session from the current user and local preferences.
         * The remembered [UserPreferences.activeRole] is honoured only if the user
         * still holds that role; otherwise Customer is preferred, then Worker.
         */
        fun resolve(user: User?, preferences: UserPreferences): SessionState = when {
            user == null -> SignedOut
            !user.hasCompletedOnboarding -> NeedsOnboarding(user)
            else -> {
                val remembered = preferences.activeRole?.takeIf { it in user.roles }
                val role = remembered
                    ?: if (UserRole.Customer in user.roles) UserRole.Customer else UserRole.Worker
                SignedIn(user, role)
            }
        }
    }
}
