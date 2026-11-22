package com.sahyog.core.ui.booking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.TimeSlot
import com.sahyog.core.ui.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * How bookings read on screen, shared by the customer's and (later) the worker's views.
 * Dates and times follow the app language (English or Hindi month/day names).
 */

@Composable
@ReadOnlyComposable
private fun appLocale(): Locale =
    LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** "Today", "Tomorrow" or e.g. "Wed, 26 Nov". */
@Composable
fun formatBookingDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.core_ui_date_today)
    today.plusDays(1) -> stringResource(R.string.core_ui_date_tomorrow)
    else -> formatDayMonth(date)
}

/** e.g. "Wed, 26 Nov". */
@Composable
fun formatDayMonth(date: LocalDate): String =
    DateTimeFormatter.ofPattern("EEE, d MMM", appLocale()).format(date)

/** Short weekday for date chips, e.g. "Wed". */
@Composable
fun formatWeekday(date: LocalDate): String = DateTimeFormatter.ofPattern("EEE", appLocale()).format(date)

/** e.g. "11 AM – 1 PM". */
@Composable
fun formatSlotTime(slot: TimeSlot): String {
    val locale = appLocale()
    return stringResource(R.string.core_ui_time_range, formatHour(slot.startTime, locale), formatHour(slot.endTime, locale))
}

private fun formatHour(time: LocalTime, locale: Locale): String =
    DateTimeFormatter.ofPattern("h a", locale).format(time)

@Composable
fun bookingStatusLabel(status: BookingStatus): String = stringResource(
    when (status) {
        BookingStatus.Requested -> R.string.core_ui_booking_status_requested
        BookingStatus.Accepted -> R.string.core_ui_booking_status_accepted
        BookingStatus.WorkerEnRoute -> R.string.core_ui_booking_status_en_route
        BookingStatus.InProgress -> R.string.core_ui_booking_status_in_progress
        BookingStatus.Completed -> R.string.core_ui_booking_status_completed
        BookingStatus.Cancelled -> R.string.core_ui_booking_status_cancelled
        BookingStatus.Rejected -> R.string.core_ui_booking_status_rejected
        BookingStatus.Expired -> R.string.core_ui_booking_status_expired
    },
)

fun bookingStatusTone(status: BookingStatus): StatusTone = when (status) {
    BookingStatus.Requested -> StatusTone.Warning
    BookingStatus.Accepted, BookingStatus.WorkerEnRoute -> StatusTone.Info
    BookingStatus.InProgress -> StatusTone.Brand
    BookingStatus.Completed -> StatusTone.Success
    BookingStatus.Cancelled, BookingStatus.Expired -> StatusTone.Neutral
    BookingStatus.Rejected -> StatusTone.Error
}
