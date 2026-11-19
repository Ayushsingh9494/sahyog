package com.sahyog.core.ui

/**
 * Display format for a 10-digit Indian mobile number: "9876543210" -> "+91 98765 43210".
 * Shared so every screen shows numbers identically.
 */
fun formatIndianPhone(number: String): String =
    if (number.length == 10) "+91 ${number.take(5)} ${number.drop(5)}" else "+91 $number"
