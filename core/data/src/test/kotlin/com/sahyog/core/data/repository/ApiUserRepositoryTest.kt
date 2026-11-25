package com.sahyog.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.network.NetworkUser
import com.sahyog.core.datastore.LocalProfileDataSource
import com.sahyog.core.model.AuthSession
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.UserState
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.testing.TestAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ApiUserRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private val auth = TestAuthRepository()
    private val session = AuthSession("u1", "+919876543210")

    private class FakeNetwork : UserNetworkDataSource {
        var me: NetworkUser? = null
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var fetches = 0
        val saved = mutableListOf<Pair<String, Set<UserRole>>>()

        override suspend fun fetchMe(): NetworkUser? {
            fetches++
            gate?.await()
            failure?.let { throw it }
            return me
        }

        override suspend fun saveMe(name: String, roles: Set<UserRole>): NetworkUser {
            saved += name to roles
            return NetworkUser("u1", "+919876543210", name, roles.map { it.name }.toSet(), workerStatus = null)
                .also { me = it }
        }
    }

    private val network = FakeNetwork()

    private fun TestScope.repository() = ApiUserRepository(
        auth,
        network,
        LocalProfileDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("p.preferences_pb") }),
        ),
    )

    private val asha = NetworkUser("u1", "+919876543210", "Asha", setOf("Customer", "Worker"), "Pending")

    @Test
    fun `signed out, then loading, then the server's profile`() = runTest {
        val repository = repository()
        network.me = asha
        network.gate = CompletableDeferred()
        repository.userState.test {
            assertEquals(UserState.SignedOut, awaitItem())
            auth.setSession(session)
            assertEquals(UserState.Loading, awaitItem())
            network.gate!!.complete(Unit)
            val ready = awaitItem() as UserState.Ready
            assertEquals("Asha", ready.user.name)
            assertEquals(setOf(UserRole.Customer, UserRole.Worker), ready.user.roles)
            assertEquals(WorkerVerificationStatus.Pending, ready.user.workerStatus)
        }
    }

    @Test
    fun `not onboarded on the server means onboarding, which saves the profile`() = runTest {
        val repository = repository()
        auth.setSession(session)
        repository.userState.test {
            var state = awaitItem()
            if (state == UserState.Loading) state = awaitItem()
            assertTrue(state is UserState.Ready && !state.user.hasCompletedOnboarding)

            val result = repository.completeOnboarding(" Asha ", setOf(UserRole.Customer))
            assertTrue(result is AppResult.Success)
            assertEquals(listOf("Asha" to setOf(UserRole.Customer)), network.saved)
            assertEquals("Asha", (awaitItem() as UserState.Ready).user.name)
        }
    }

    @Test
    fun `offline with nothing cached is unavailable until a retry works`() = runTest {
        val repository = repository()
        network.failure = IOException("offline")
        auth.setSession(session)
        repository.userState.test {
            var state = awaitItem()
            if (state == UserState.Loading) state = awaitItem()
            assertEquals(UserState.Unavailable, state)

            network.failure = null
            network.me = asha
            assertEquals(AppResult.Success(Unit), repository.refresh())
            var next = awaitItem()
            while (next !is UserState.Ready) next = awaitItem()
            assertEquals("Asha", next.user.name)
        }
    }

    @Test
    fun `a cached profile opens immediately and stays usable offline`() = runTest {
        val repository = repository()
        network.me = asha
        auth.setSession(session)
        repository.refresh()
        network.failure = IOException("offline")
        assertEquals(AppResult.Failure(AppError.Network), repository.refresh())
        repository.userState.test {
            var state = awaitItem()
            while (state !is UserState.Ready) state = awaitItem()
            assertEquals("Asha", state.user.name)
        }
    }

    @Test
    fun `worker approval updates the cached status, and sign-out clears the cache`() = runTest {
        val repository = repository()
        network.me = asha
        auth.setSession(session)
        repository.refresh()
        repository.onWorkerStatus(WorkerVerificationStatus.Verified)
        repository.currentUser.test {
            var user = awaitItem()
            while (user?.workerStatus != WorkerVerificationStatus.Verified) user = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        repository.clearUserData()
        network.failure = IOException("offline")
        repository.userState.test {
            var state = awaitItem()
            while (state == UserState.Loading) state = awaitItem()
            assertEquals(UserState.Unavailable, state)
        }
    }

    @Test
    fun `invalid onboarding input never reaches the server`() = runTest {
        val repository = repository()
        auth.setSession(session)
        assertEquals(
            AppResult.Failure(AppError.Validation(field = "name")),
            repository.completeOnboarding("A", setOf(UserRole.Customer)),
        )
        assertEquals(
            AppResult.Failure(AppError.Validation(field = "roles")),
            repository.completeOnboarding("Asha", emptySet()),
        )
        assertTrue(network.saved.isEmpty())
    }
}
