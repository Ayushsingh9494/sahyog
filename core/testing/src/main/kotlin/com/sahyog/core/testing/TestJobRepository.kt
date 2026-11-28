package com.sahyog.core.testing

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.data.repository.JobRepository
import com.sahyog.core.data.worker.WorkerErrorFields
import com.sahyog.core.model.BookingPayment
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.PaymentMethod
import com.sahyog.core.model.Job
import com.sahyog.core.model.JobAction
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.TimeSlot
import com.sahyog.core.model.allowedJobActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate

/** In-memory jobs that follow the real lifecycle rules (and the start code "4821"). */
class TestJobRepository : JobRepository {
    private val jobsFlow = MutableStateFlow<List<Job>>(emptyList())

    var refreshCount = 0
        private set
    var failNextAction: AppError? = null

    fun setJobs(jobs: List<Job>) = jobsFlow.update { jobs }

    override val jobs: Flow<List<Job>> = jobsFlow

    override fun job(id: String): Flow<Job?> = jobsFlow.map { list -> list.find { it.id == id } }

    override suspend fun refresh(): AppResult<Unit> {
        refreshCount++
        return AppResult.Success(Unit)
    }

    override suspend fun accept(jobId: String) = step(jobId, JobAction.Accept) { it.copy(status = BookingStatus.Accepted) }

    override suspend fun decline(jobId: String) = step(jobId, JobAction.Decline) { it.copy(status = BookingStatus.Rejected) }

    override suspend fun startTrip(jobId: String) =
        step(jobId, JobAction.StartTrip) { it.copy(status = BookingStatus.WorkerEnRoute) }

    override suspend fun startJob(jobId: String, startCode: String): AppResult<Job> {
        if (startCode != TestJobs.START_CODE) return AppResult.Failure(AppError.Validation(WorkerErrorFields.START_CODE))
        return step(jobId, JobAction.StartJob) { it.copy(status = BookingStatus.InProgress) }
    }

    override suspend fun complete(jobId: String) =
        step(jobId, JobAction.Complete) { it.copy(status = BookingStatus.Completed, completedAt = Instant.EPOCH) }

    override suspend fun markCashReceived(jobId: String): AppResult<Job> {
        failNextAction?.let {
            failNextAction = null
            return AppResult.Failure(it)
        }
        val job = jobsFlow.value.find { it.id == jobId } ?: return AppResult.Failure(AppError.NotFound)
        if (job.status != BookingStatus.Completed || job.payment.isPaid) {
            return AppResult.Failure(AppError.Validation(BookingErrorFields.PAYMENT))
        }
        val updated = job.copy(payment = job.payment.copy(isPaid = true, via = PaymentMethod.Cash, paidAt = Instant.EPOCH))
        jobsFlow.update { list -> list.map { if (it.id == jobId) updated else it } }
        return AppResult.Success(updated)
    }

    private fun step(jobId: String, action: JobAction, change: (Job) -> Job): AppResult<Job> {
        failNextAction?.let {
            failNextAction = null
            return AppResult.Failure(it)
        }
        val job = jobsFlow.value.find { it.id == jobId } ?: return AppResult.Failure(AppError.NotFound)
        if (action !in job.status.allowedJobActions()) {
            return AppResult.Failure(AppError.Validation(BookingErrorFields.STATUS))
        }
        val updated = change(job)
        jobsFlow.update { list -> list.map { if (it.id == jobId) updated else it } }
        return AppResult.Success(updated)
    }
}

object TestJobs {
    const val START_CODE = "4821"
    val date: LocalDate = LocalDate.of(2026, 11, 25)

    fun job(
        id: String,
        status: BookingStatus = BookingStatus.Requested,
        slot: TimeSlot = TimeSlot(date, 11),
        rupees: Long = 300,
        completedAt: Instant? = null,
        payment: BookingPayment = BookingPayment(),
    ) = Job(
        id = id,
        customerName = "Asha Devi",
        serviceName = "Fan repair",
        price = Paise.rupees(rupees),
        priceUnit = PriceUnit.PerVisit,
        slot = slot,
        address = "12 Main Road, Model Town",
        note = null,
        status = status,
        createdAt = Instant.EPOCH,
        completedAt = completedAt,
        payment = payment,
    )
}
