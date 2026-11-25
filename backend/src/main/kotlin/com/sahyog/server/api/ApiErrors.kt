package com.sahyog.server.api

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * The one error shape the API returns, e.g. `{"code":"validation","field":"name"}`.
 *
 * The HTTP status says what kind of failure it is (the app maps 400 → `AppError.Validation`,
 * 401 → Unauthorized, 403 → Forbidden, 404 → NotFound, 429 → RateLimited, 5xx → Server);
 * [field] says which input was wrong, using the same names as the app's `*ErrorFields`.
 * Messages are never sent: the app shows its own translated text.
 */
data class ErrorBody(val code: String, val field: String? = null)

/** Thrown by services to refuse a request; turned into [ErrorBody] by [ApiExceptionHandler]. */
open class ApiException(val status: HttpStatus, val code: String, val field: String? = null) :
    RuntimeException("$code${field?.let { " ($it)" }.orEmpty()}")

class ValidationException(field: String) : ApiException(HttpStatus.BAD_REQUEST, "validation", field)

class NotFoundException : ApiException(HttpStatus.NOT_FOUND, "not_found")

class ForbiddenException : ApiException(HttpStatus.FORBIDDEN, "forbidden")

class UnauthorizedException : ApiException(HttpStatus.UNAUTHORIZED, "unauthorized")

/** The generic code for a status, for errors that don't come from our own services. */
fun codeFor(status: HttpStatusCode): String = when (status.value()) {
    400 -> "validation"
    401 -> "unauthorized"
    403 -> "forbidden"
    404 -> "not_found"
    405 -> "method_not_allowed"
    409 -> "conflict"
    413 -> "too_large"
    415 -> "unsupported_media_type"
    429 -> "rate_limited"
    else -> if (status.is4xxClientError) "bad_request" else "server"
}

@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handle(e: ApiException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(e.status).body(ErrorBody(e.code, e.field))

    /** Bean-validation failures on a request body: report the first offending field. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handle(e: MethodArgumentNotValidException): ResponseEntity<ErrorBody> =
        ResponseEntity.badRequest().body(ErrorBody("validation", e.bindingResult.fieldErrors.firstOrNull()?.field))

    /** Malformed JSON, or a missing / wrongly typed property. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handle(e: HttpMessageNotReadableException): ResponseEntity<ErrorBody> =
        ResponseEntity.badRequest().body(ErrorBody("validation"))

    @ExceptionHandler(AccessDeniedException::class)
    fun handle(e: AccessDeniedException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorBody("forbidden"))

    /**
     * Spring MVC's own errors (unknown path, wrong method, ...) keep their status. Anything
     * else is unexpected: log it here, tell the client only that it failed.
     */
    @ExceptionHandler(Exception::class)
    fun handle(e: Exception): ResponseEntity<ErrorBody> {
        if (e is ErrorResponse) return ResponseEntity.status(e.statusCode).body(ErrorBody(codeFor(e.statusCode)))
        log.error("Unhandled error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorBody("server"))
    }
}
