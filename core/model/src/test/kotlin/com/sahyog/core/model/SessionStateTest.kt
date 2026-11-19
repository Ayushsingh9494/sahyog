package com.sahyog.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateTest {

    private fun user(
        name: String? = "Asha",
        roles: Set<UserRole> = setOf(UserRole.Customer),
    ) = User(
        id = "u1",
        phoneNumber = "9876543210",
        name = name,
        roles = roles,
        workerStatus = if (UserRole.Worker in roles) WorkerVerificationStatus.Pending else null,
    )

    @Test
    fun `no user means signed out`() {
        assertEquals(SessionState.SignedOut, SessionState.resolve(null, UserPreferences()))
    }

    @Test
    fun `user without name needs onboarding`() {
        val u = user(name = null)
        assertEquals(SessionState.NeedsOnboarding(u), SessionState.resolve(u, UserPreferences()))
    }

    @Test
    fun `user without roles needs onboarding`() {
        val u = user(roles = emptySet())
        assertEquals(SessionState.NeedsOnboarding(u), SessionState.resolve(u, UserPreferences()))
    }

    @Test
    fun `remembered role is used when user still holds it`() {
        val u = user(roles = setOf(UserRole.Customer, UserRole.Worker))
        val state = SessionState.resolve(u, UserPreferences(activeRole = UserRole.Worker))
        assertEquals(SessionState.SignedIn(u, UserRole.Worker), state)
    }

    @Test
    fun `remembered role is ignored when user no longer holds it`() {
        val u = user(roles = setOf(UserRole.Customer))
        val state = SessionState.resolve(u, UserPreferences(activeRole = UserRole.Worker))
        assertEquals(SessionState.SignedIn(u, UserRole.Customer), state)
    }

    @Test
    fun `worker-only user defaults to worker mode`() {
        val u = user(roles = setOf(UserRole.Worker))
        assertEquals(SessionState.SignedIn(u, UserRole.Worker), SessionState.resolve(u, UserPreferences()))
    }
}
