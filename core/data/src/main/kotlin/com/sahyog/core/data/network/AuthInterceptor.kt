package com.sahyog.core.data.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The signed-in user's Firebase ID token: proof of identity for the Sahyog server. */
interface IdTokenProvider {
    /**
     * Blocking; called on OkHttp's background threads. Null when signed out.
     * @param forceRefresh Ask Firebase for a new token even if the cached one looks valid.
     */
    fun idToken(forceRefresh: Boolean): String?
}

internal class FirebaseIdTokenProvider @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : IdTokenProvider {
    override fun idToken(forceRefresh: Boolean): String? {
        val user = firebaseAuth.currentUser ?: return null
        // Firebase caches the token (valid for an hour) and refreshes it itself when needed.
        return runCatching { Tasks.await(user.getIdToken(forceRefresh), TIMEOUT_SECONDS, TimeUnit.SECONDS).token }
            .getOrNull()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
    }
}

/** Adds `Authorization: Bearer <Firebase ID token>` to every request. */
internal class AuthInterceptor @Inject constructor(private val tokens: IdTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.idToken(forceRefresh = false) ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().withToken(token))
    }
}

/**
 * On a 401 (e.g. the token expired between Firebase's check and the server's), retries once
 * with a freshly issued token. A second 401 is final: the app treats it as signed out.
 */
internal class TokenRefreshAuthenticator @Inject constructor(private val tokens: IdTokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val fresh = tokens.idToken(forceRefresh = true) ?: return null
        return response.request.withToken(fresh)
    }
}

private fun Request.withToken(token: String): Request =
    newBuilder().header("Authorization", "Bearer $token").build()
