package com.sahyog.core.data.demo

import android.content.Context
import com.sahyog.core.common.dispatcher.Dispatcher
import com.sahyog.core.common.dispatcher.SahyogDispatcher
import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.booking.NetworkBooking
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.worker.NetworkWorkerProfile
import com.sahyog.core.datastore.LocalProfileDataSource
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.WorkerVerificationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Everything the pretend server stores. Users (names, roles) live in [LocalProfileDataSource]. */
@Serializable
internal data class DemoState(
    val bookings: List<StoredBooking> = emptyList(),
    /** Worker profiles of real (signed-up) users, keyed by uid. */
    val workers: Map<String, NetworkWorkerProfile> = emptyMap(),
)

@Serializable
internal data class StoredBooking(
    val customerUid: String,
    val booking: NetworkBooking,
)

/**
 * TEMPORARY stand-in for the Sahyog backend, until the Spring Boot API exists (Milestone 10).
 *
 * Holds the shared state behind the demo catalog, booking and worker data sources in one
 * JSON file in app storage (it survives sign-out, like a server database), serialises all
 * requests with a lock, simulates latency, and applies time-based server behaviour:
 * - fictional catalog workers accept requests after [AUTO_ACCEPT_SECONDS];
 * - requests nobody accepted expire when their slot starts;
 * - the cooperative "verifies" a worker [AUTO_VERIFY_SECONDS] after they submit an ID.
 */
@Singleton
internal class DemoServer(
    private val stateFile: File,
    private val authRepository: AuthRepository,
    private val profiles: LocalProfileDataSource,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    val random: Random = Random.Default,
    private val latencyMs: Long = SIMULATED_LATENCY_MS,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        authRepository: AuthRepository,
        profiles: LocalProfileDataSource,
        clock: Clock,
        @Dispatcher(SahyogDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(File(context.filesDir, "demo_server/state.json"), authRepository, profiles, clock, ioDispatcher)

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /** Mutable view of the state inside one request. */
    inner class Transaction(var state: DemoState) {
        val now: LocalDateTime get() = LocalDateTime.now(clock)
        val nowMillis: Long get() = clock.millis()

        suspend fun userName(uid: String): String? = profiles.profile(uid).first()?.name
    }

    /** A request on behalf of the signed-in user. */
    suspend fun <T> call(block: suspend Transaction.(uid: String) -> T): T {
        delay(latencyMs)
        val uid = authRepository.session.first()?.uid ?: throw ApiException(AppError.Unauthorized)
        return transaction { block(uid) }
    }

    /** A request that needs no signed-in user (e.g. reading the public catalog). */
    suspend fun <T> publicCall(block: suspend Transaction.() -> T): T {
        delay(latencyMs)
        return transaction { block() }
    }

    private suspend fun <T> transaction(block: suspend Transaction.() -> T): T = withContext(ioDispatcher) {
        mutex.withLock {
            val loaded = load()
            val tx = Transaction(loaded)
            tx.state = tx.advance(loaded)
            val result = tx.block()
            if (tx.state != loaded) save(tx.state)
            result
        }
    }

    /** What would have happened on a real server since the last request. */
    private suspend fun Transaction.advance(state: DemoState): DemoState {
        val workers = state.workers.mapValues { (uid, worker) ->
            val submittedAt = worker.documentSubmittedAtMillis
            val due = submittedAt != null && nowMillis - submittedAt >= AUTO_VERIFY_SECONDS * 1_000
            if (worker.status == WorkerVerificationStatus.Pending.name && due) {
                profiles.updateWorkerStatus(uid, WorkerVerificationStatus.Verified)
                worker.copy(status = WorkerVerificationStatus.Verified.name)
            } else {
                worker
            }
        }
        val bookings = state.bookings.map { stored ->
            stored.copy(booking = advanceBooking(stored.booking, isRealWorker = stored.booking.workerId in workers))
        }
        return DemoState(bookings = bookings, workers = workers)
    }

    private fun Transaction.advanceBooking(booking: NetworkBooking, isRealWorker: Boolean): NetworkBooking {
        if (booking.status != BookingStatus.Requested.name) return booking
        val slotStart = runCatching { LocalDate.parse(booking.slotDate).atTime(booking.slotStartHour, 0) }.getOrNull()
        return when {
            slotStart != null && !now.isBefore(slotStart) -> booking.copy(status = BookingStatus.Expired.name)
            // Real workers answer requests themselves; only the fictional ones are simulated.
            !isRealWorker && nowMillis - booking.createdAtMillis >= AUTO_ACCEPT_SECONDS * 1_000 ->
                booking.copy(status = BookingStatus.Accepted.name, startCode = newStartCode())
            else -> booking
        }
    }

    fun newStartCode(): String = random.nextInt(1_000, 10_000).toString()

    /** e.g. "SH-7K3Q9P": short enough to read out over the phone. */
    fun newBookingId(): String = "SH-" + (1..6).map { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] }.joinToString("")

    private fun load(): DemoState =
        if (stateFile.exists()) {
            runCatching { json.decodeFromString<DemoState>(stateFile.readText()) }.getOrDefault(DemoState())
        } else {
            DemoState()
        }

    private fun save(state: DemoState) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(json.encodeToString(state))
    }

    companion object {
        const val AUTO_ACCEPT_SECONDS = 20L
        const val AUTO_VERIFY_SECONDS = 30L
        private const val SIMULATED_LATENCY_MS = 600L

        // No 0/O or 1/I, which are easy to confuse when read aloud.
        private const val ID_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
