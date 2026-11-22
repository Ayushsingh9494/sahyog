package com.sahyog.core.data.auth

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.sahyog.core.common.dispatcher.ApplicationScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.common.result.map
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.OtpRequestResult
import com.sahyog.core.data.session.UserDataCleaner
import dagger.Lazy
import com.sahyog.core.datastore.SahyogPreferencesDataSource
import com.sahyog.core.model.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val COUNTRY_CODE = "+91"

// Firebase waits this long for SMS auto-retrieval before giving up on it.
private const val AUTO_RETRIEVAL_TIMEOUT_SECONDS = 60L

private const val TAG = "FirebaseAuthRepository"

// Upper bound for the whole request. Guards against callbacks never arriving, e.g. when
// Firebase auto-detaches them because the app went to the background mid-request.
private const val REQUEST_TIMEOUT_MS = 75_000L

@Singleton
internal class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val activityTracker: ForegroundActivityTracker,
    private val preferences: SahyogPreferencesDataSource,
    @param:ApplicationScope private val appScope: CoroutineScope,
    // Lazy breaks a dependency cycle: cleaners (e.g. the booking repository) may
    // themselves depend on AuthRepository.
    private val userDataCleaners: Lazy<Set<@JvmSuppressWildcards UserDataCleaner>>,
) : AuthRepository {

    /** State of the last code request, needed to verify a code or re-send one. Memory-only by design. */
    private data class PendingVerification(
        val phoneNumber: String,
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken,
    )

    @Volatile private var pending: PendingVerification? = null

    // Firebase persists the signed-in user itself and reports it on registration,
    // so the session is restored on app start without any extra storage.
    override val session: Flow<AuthSession?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.toSession()) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun requestOtp(phoneNumber: String, resend: Boolean): AppResult<OtpRequestResult> =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) { sendCode(phoneNumber, resend) }
            ?: AppResult.Failure(AppError.Network)

    private suspend fun sendCode(phoneNumber: String, resend: Boolean): AppResult<OtpRequestResult> =
        suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)
            fun complete(result: AppResult<OtpRequestResult>) {
                if (resumed.compareAndSet(false, true) && continuation.isActive) continuation.resume(result)
            }

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    pending = PendingVerification(phoneNumber, verificationId, token)
                    complete(AppResult.Success(OtpRequestResult.CodeSent))
                }

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Instant verification (before onCodeSent) or SMS auto-retrieval (after it).
                    // Sign in on the app scope: the OTP screen may already be showing, and the
                    // resulting session change moves the app on wherever the user is.
                    appScope.launch {
                        val result = signIn(credential)
                        complete(result.map { OtpRequestResult.SignedIn })
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    complete(AppResult.Failure(e.toAuthAppError()))
                }
            }

            val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(COUNTRY_CODE + phoneNumber)
                .setTimeout(AUTO_RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .setCallbacks(callbacks)
                .apply {
                    // Needed only for the reCAPTCHA fallback on devices without Play Integrity.
                    activityTracker.activity?.let(::setActivity)
                    pending
                        ?.takeIf { resend && it.phoneNumber == phoneNumber }
                        ?.let { setForceResendingToken(it.resendToken) }
                }
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }

    override suspend fun verifyOtp(phoneNumber: String, code: String): AppResult<Unit> {
        // No pending request (e.g. the app process was killed while on the OTP screen):
        // the user has to request a fresh code.
        val request = pending?.takeIf { it.phoneNumber == phoneNumber }
            ?: return AppResult.Failure(AppError.Validation(AuthErrorFields.OTP_EXPIRED))
        return signIn(PhoneAuthProvider.getCredential(request.verificationId, code))
    }

    private suspend fun signIn(credential: PhoneAuthCredential): AppResult<Unit> = try {
        firebaseAuth.signInWithCredential(credential).await()
        pending = null
        AppResult.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(e.toAuthAppError())
    }

    override suspend fun signOut() {
        pending = null
        // Wipe this person's cached data first. A failing cleaner must not block sign-out.
        userDataCleaners.get().forEach { cleaner ->
            try {
                cleaner.clearUserData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear ${cleaner::class.simpleName} on sign-out", e)
            }
        }
        firebaseAuth.signOut()
        // The remembered mode belongs to the person who just left.
        preferences.setActiveRole(null)
    }
}

private fun FirebaseUser.toSession() = AuthSession(
    uid = uid,
    phoneNumber = phoneNumber.orEmpty().removePrefix(COUNTRY_CODE),
)
