package com.sahyog.core.common.validation

/**
 * Indian mobile numbers: 10 digits starting with 6-9 (TRAI numbering plan).
 * Input is expected without the +91 prefix, which the UI shows separately.
 */
object PhoneNumberValidator {
    const val LENGTH = 10
    private val pattern = Regex("^[6-9]\\d{9}$")

    fun isValid(number: String): Boolean = pattern.matches(number)

    /**
     * Input filter for the phone field. Keeps digits only and strips a leading
     * country code (91) or trunk prefix (0) when a full number is pasted,
     * e.g. "+91 98765 43210" or "09876543210" both become "9876543210".
     */
    fun sanitize(input: String): String {
        val digits = input.filter(Char::isDigit)
        val local = when {
            digits.length > LENGTH && digits.startsWith("91") -> digits.removePrefix("91")
            digits.length > LENGTH && digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
        return local.take(LENGTH)
    }
}

object OtpValidator {
    const val LENGTH = 6

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all(Char::isDigit)

    fun sanitize(input: String): String = input.filter(Char::isDigit).take(LENGTH)
}

object NameValidator {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 60

    /** Letters in any script, spaces, dots, apostrophes and hyphens. */
    private val allowed = Regex("^[\\p{L}\\p{M} .'-]+$")

    fun isValid(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.length in MIN_LENGTH..MAX_LENGTH && allowed.matches(trimmed)
    }
}
