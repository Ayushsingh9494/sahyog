package com.sahyog.core.model

/**
 * The identity proven by phone verification: who the person is, and nothing else.
 * The profile (name, roles) is separate and owned by the backend; see [User].
 *
 * @property uid Stable user id issued by the auth provider (Firebase).
 * @property phoneNumber 10-digit Indian mobile number without country code.
 */
data class AuthSession(
    val uid: String,
    val phoneNumber: String,
)
