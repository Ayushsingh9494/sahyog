package com.sahyog.core.data.demo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sahyog.core.data.booking.DemoBookingDataSource
import com.sahyog.core.data.catalog.DemoCatalogDataSource
import com.sahyog.core.data.worker.DemoWorkerDataSource
import com.sahyog.core.datastore.LocalProfileDataSource
import com.sahyog.core.datastore.StoredProfile
import com.sahyog.core.model.AuthSession
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.testing.TestAuthRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/** A demo server on temp files with a controllable clock and signed-in user. */
internal class DemoTestEnvironment(tmp: TemporaryFolder, scope: TestScope) {

    var now: LocalDateTime = LocalDateTime.of(2026, 11, 23, 8, 0)

    private val testZone: ZoneId = ZoneOffset.UTC
    val clock = object : Clock() {
        override fun getZone(): ZoneId = testZone
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now.toInstant(ZoneOffset.UTC)
    }

    val auth = TestAuthRepository()

    val profiles = LocalProfileDataSource(
        PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { tmp.newFile("profiles.preferences_pb") },
        ),
    )

    val server = DemoServer(
        stateFile = tmp.root.resolve("state.json"),
        authRepository = auth,
        profiles = profiles,
        clock = clock,
        ioDispatcher = StandardTestDispatcher(scope.testScheduler),
        random = Random(42),
        latencyMs = 0,
    )

    val catalog = DemoCatalogDataSource(server)
    val bookings = DemoBookingDataSource(server, catalog)
    val workers = DemoWorkerDataSource(server, profiles)

    /** Signs in as [uid], creating their user profile if needed. */
    suspend fun signIn(uid: String, name: String = "User $uid", roles: Set<UserRole> = setOf(UserRole.Customer)) {
        profiles.save(
            uid,
            StoredProfile(
                name = name,
                roles = roles,
                workerStatus = if (UserRole.Worker in roles) WorkerVerificationStatus.NotSubmitted else null,
            ),
        )
        auth.setSession(AuthSession(uid, "9876543210"))
    }

    fun signOut() = auth.setSession(null)
}
