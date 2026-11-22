package com.sahyog.core.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.ui.R

/**
 * Indian rupee display: 149900 paise -> "₹1,499"; 12345678 -> "₹1,23,456.78".
 * Uses Indian digit grouping (lakh/crore) and drops ".00" for whole amounts.
 * Pure Kotlin, so it behaves the same on every device and in unit tests.
 */
fun formatRupees(amount: Paise): String {
    val negative = amount.value < 0
    val absolute = kotlin.math.abs(amount.value)
    val rupees = groupIndian((absolute / 100).toString())
    val paise = absolute % 100
    val decimals = if (paise == 0L) "" else ".${paise.toString().padStart(2, '0')}"
    return (if (negative) "-₹" else "₹") + rupees + decimals
}

/** "1234567" -> "12,34,567": last three digits, then groups of two. */
private fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    val groups = head.reversed().chunked(2).joinToString(",").reversed()
    return "$groups,$tail"
}

/** "/visit", "/hour", "/job" (translated). */
@Composable
fun priceUnitSuffix(unit: PriceUnit): String = stringResource(
    when (unit) {
        PriceUnit.PerVisit -> R.string.core_ui_price_per_visit
        PriceUnit.PerHour -> R.string.core_ui_price_per_hour
        PriceUnit.PerJob -> R.string.core_ui_price_per_job
    },
)
