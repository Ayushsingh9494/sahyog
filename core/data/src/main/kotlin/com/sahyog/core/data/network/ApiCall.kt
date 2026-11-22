package com.sahyog.core.data.network

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A request the server understood and refused, carrying the reason as an [AppError].
 * Network data sources (demo now, Retrofit in M10) throw this for 4xx-style answers.
 */
class ApiException(val error: AppError) : Exception(error.toString())

/** Runs a network call and maps every failure to an [AppError] exactly once. */
internal suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: ApiException) {
    AppResult.Failure(e.error)
} catch (e: IOException) {
    AppResult.Failure(AppError.Network)
} catch (e: Exception) {
    AppResult.Failure(AppError.Unknown(e))
}
