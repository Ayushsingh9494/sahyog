package com.sahyog.core.data.network

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A request the server understood and refused, carrying the reason as an [AppError].
 * Data sources throw this for answers they interpret themselves.
 */
class ApiException(val error: AppError) : Exception(error.toString())

/** Runs a network call and maps every failure to an [AppError] exactly once. */
internal suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: ApiException) {
    AppResult.Failure(e.error)
} catch (e: HttpException) {
    AppResult.Failure(e.toAppError())
} catch (e: IOException) {
    AppResult.Failure(AppError.Network)
} catch (e: Exception) {
    AppResult.Failure(AppError.Unknown(e))
}

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * The HTTP status says what kind of failure it is; the server's `{code, field}` body says
 * which input was wrong (field names match the app's `*ErrorFields`).
 */
internal fun HttpException.toAppError(): AppError {
    val body = runCatching {
        response()?.errorBody()?.string()?.let { errorJson.decodeFromString<NetworkError>(it) }
    }.getOrNull()
    return when (code()) {
        400, 409, 413, 422 -> AppError.Validation(field = body?.field)
        401 -> AppError.Unauthorized
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        429 -> AppError.RateLimited
        else -> AppError.Server(code())
    }
}
