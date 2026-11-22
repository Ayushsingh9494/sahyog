package com.sahyog.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.datastore.LocalProfileDataSource
import com.sahyog.core.model.AuthSession
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.testing.TestAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LocalProfileUserRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val auth = TestAuthRepository()

    // A real DataStore on a temp file: exercises actual persistence, not a mock.
    private val repository by lazy {
        val store = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { tmp.newFile("profiles.preferences_pb") },
        )
        LocalProfileUserRepository(auth, LocalProfileDataSource(store))
    }

    private val asha = AuthSession(uid = "uid-asha", phoneNumber = "9876543210")

    @Test
    fun `signed out means no user`() = testScope.runTest {
        repository.currentUser.test { assertNull(awaitItem()) }
    }

    @Test
    fun `new session without profile needs onboarding`() = testScope.runTest {
        auth.setSession(asha)
        repository.currentUser.test {
            val user = awaitItem()!!
            assertEquals("uid-asha", user.id)
            assertEquals(false, user.hasCompletedOnboarding)
        }
    }

    @Test
    fun `onboarding saves the profile and workers start unverified`() = testScope.runTest {
        auth.setSession(asha)
        val result = repository.completeOnboarding("  Asha Verma ", setOf(UserRole.Customer, UserRole.Worker))

        val user = (result as AppResult.Success).data
        assertEquals("Asha Verma", user.name)
        assertEquals(WorkerVerificationStatus.NotSubmitted, user.workerStatus)
        repository.currentUser.test { assertEquals(user, awaitItem()) }
    }

    @Test
    fun `profile survives sign out and returns on next sign in`() = testScope.runTest {
        auth.setSession(asha)
        repository.completeOnboarding("Asha", setOf(UserRole.Customer))

        repository.currentUser.test {
            assertEquals("Asha", awaitItem()?.name)
            auth.signOut()
            assertNull(awaitItem())
            auth.setSession(asha)
            assertEquals("Asha", awaitItem()?.name)
        }
    }

    @Test
    fun `onboarding without a session is unauthorized`() = testScope.runTest {
        assertEquals(
            AppResult.Failure(AppError.Unauthorized),
            repository.completeOnboarding("Asha", setOf(UserRole.Customer)),
        )
    }
}
