package com.sahyog.core.ui

/**
 * Display format for an Indian mobile number, given as 10 digits or in E.164 ("+919876543210"):
 * "+91 98765 43210". Shared so every screen shows numbers identically.
 */
fun formatIndianPhone(number: String): String {
    val local = number.removePrefix("+91").takeIf { number.startsWith("+91") && it.length == 10 } ?: number
    return if (local.length == 10 && local.all(Char::isDigit)) "+91 ${local.take(5)} ${local.drop(5)}" else number
}
