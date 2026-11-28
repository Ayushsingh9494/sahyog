package com.sahyog.core.data.worker

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.booking.NetworkBooking
import com.sahyog.core.data.booking.asExternalModel
import com.sahyog.core.data.booking.paymentModel
import com.sahyog.core.data.network.apiCall
import com.sahyog.core.data.repository.JobRepository
import com.sahyog.core.data.session.UserDataCleaner
import com.sahyog.core.database.dao.JobDao
import com.sahyog.core.database.entity.asEntity
import com.sahyog.core.database.entity.asExternalModel
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.model.Job
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in worker's jobs. Like bookings: reads from the Room cache, every status
 * change goes to the server first and only its answer is cached.
 */
@Singleton
internal class OfflineFirstJobRepository @Inject constructor(
    private val dao: JobDao,
    private val network: WorkerNetworkDataSource,
) : JobRepository, UserDataCleaner {

    override val jobs: Flow<List<Job>> = dao.observeJobs()
        .map { entities -> entities.map { it.asExternalModel() } }
        .distinctUntilChanged()

    override fun job(id: String): Flow<Job?> = dao.observeJob(id).map { it?.asExternalModel() }.distinctUntilChanged()

    override suspend fun refresh(): AppResult<Unit> = apiCall {
        dao.replaceAll(network.fetchJobs().map { it.asJob().asEntity() })
    }

    override suspend fun accept(jobId: String) = update { network.acceptJob(jobId) }

    override suspend fun decline(jobId: String) = update { network.declineJob(jobId) }

    override suspend fun startTrip(jobId: String) = update { network.startTrip(jobId) }

    override suspend fun startJob(jobId: String, startCode: String) = update { network.startJob(jobId, startCode) }

    override suspend fun complete(jobId: String) = update { network.completeJob(jobId) }

    override suspend fun markCashReceived(jobId: String) = update { network.markCashReceived(jobId) }

    override suspend fun clearUserData() = dao.deleteAll()

    private suspend fun update(call: suspend () -> NetworkBooking): AppResult<Job> = apiCall {
        call().asJob().also { dao.upsert(it.asEntity()) }
    }
}

internal fun NetworkBooking.asJob() = Job(
    id = id,
    customerName = customerName,
    serviceName = serviceName,
    price = Paise(pricePaise),
    priceUnit = PriceUnit.entries.firstOrNull { it.name == priceUnit } ?: PriceUnit.PerVisit,
    slot = TimeSlot(LocalDate.parse(slotDate), slotStartHour),
    address = address,
    note = note,
    status = BookingStatus.entries.firstOrNull { it.name == status } ?: BookingStatus.Requested,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    completedAt = completedAtMillis?.let(Instant::ofEpochMilli),
    review = review?.asExternalModel(),
    location = GeoPoint.ofOrNull(latitude, longitude),
    payment = paymentModel(),
)
