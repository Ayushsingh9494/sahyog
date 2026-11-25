package com.sahyog.feature.booking.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.Booking
import com.sahyog.core.ui.booking.bookingStatusLabel
import com.sahyog.core.ui.booking.bookingStatusTone
import com.sahyog.core.ui.booking.formatBookingDate
import com.sahyog.core.ui.booking.formatSlotTime
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.booking.R
import java.time.LocalDate

/** Booking-specific wording for errors, falling back to the generic messages. */
@StringRes
internal fun AppError.bookingMessageRes(): Int = when {
    this is AppError.Validation && field == BookingErrorFields.SLOT_TAKEN -> R.string.booking_error_slot_taken
    this is AppError.Validation && field == BookingErrorFields.SLOT -> R.string.booking_error_slot_unavailable
    this is AppError.Validation && (field == BookingErrorFields.WORKER || field == BookingErrorFields.SERVICE) ->
        R.string.booking_error_worker_gone
    this is AppError.Validation && field == BookingErrorFields.STATUS -> R.string.booking_cancel_not_allowed
    this is AppError.Validation && field == BookingErrorFields.REVIEW_CLOSED -> R.string.booking_error_review_closed
    this is AppError.Validation && field == BookingErrorFields.REVIEW -> R.string.booking_error_review_invalid
    else -> messageRes()
}

/** "Tomorrow · 11 AM – 1 PM" */
@Composable
internal fun bookingWhen(booking: Booking, today: LocalDate): String = stringResource(
    R.string.booking_when,
    formatBookingDate(booking.slot.date, today),
    formatSlotTime(booking.slot),
)

@Composable
internal fun BookingCard(
    booking: Booking,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    awaitingReview: Boolean = false,
) {
    SahyogCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SahyogAvatar(name = booking.worker.name, size = AvatarSize)
            Spacer(Modifier.width(SahyogSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xxs)) {
                Text(
                    text = booking.serviceName,
                    style = SahyogTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = booking.worker.name,
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = bookingWhen(booking, today),
                    style = SahyogTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(SahyogSpacing.sm))
            if (awaitingReview) {
                // A nudge to rate replaces "Completed": the status is already clear from "Past".
                SahyogStatusBadge(text = stringResource(R.string.booking_review_to_rate), tone = StatusTone.Brand)
            } else {
                SahyogStatusBadge(text = bookingStatusLabel(booking.status), tone = bookingStatusTone(booking.status))
            }
        }
    }
}

private val AvatarSize = 48.dp
