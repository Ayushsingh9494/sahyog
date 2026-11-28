package com.sahyog.feature.worker.component

import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.model.isPaymentDue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.Job
import com.sahyog.core.ui.booking.bookingStatusLabel
import com.sahyog.core.ui.booking.bookingStatusTone
import com.sahyog.core.ui.booking.formatBookingDate
import com.sahyog.core.ui.booking.formatSlotTime
import com.sahyog.core.ui.catalog.formatRupees
import com.sahyog.core.ui.catalog.priceUnitSuffix
import com.sahyog.feature.worker.R
import java.time.LocalDate

@Composable
internal fun customerName(job: Job): String = job.customerName ?: stringResource(R.string.worker_customer_unknown)

/** "Tomorrow · 11 AM – 1 PM" */
@Composable
internal fun jobWhen(job: Job, today: LocalDate): String =
    stringResource(R.string.worker_when, formatBookingDate(job.slot.date, today), formatSlotTime(job.slot))

/**
 * A job in a list. With [onAccept] (new requests) it shows an Accept button, so the
 * worker can take a job without opening it.
 */
@Composable
internal fun JobCard(
    job: Job,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAccept: (() -> Unit)? = null,
    accepting: Boolean = false,
) {
    SahyogCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SahyogAvatar(name = customerName(job), size = AvatarSize)
            Spacer(Modifier.width(SahyogSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xxs)) {
                Text(
                    text = job.serviceName,
                    style = SahyogTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = customerName(job),
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
                Text(text = jobWhen(job, today), style = SahyogTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(SahyogSpacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                if (job.isPaymentDue) {
                    SahyogStatusBadge(text = stringResource(R.string.worker_badge_to_collect), tone = StatusTone.Warning)
                } else {
                    SahyogStatusBadge(text = bookingStatusLabel(job.status), tone = bookingStatusTone(job.status))
                }
                Spacer(Modifier.height(SahyogSpacing.xs))
                Text(
                    text = formatRupees(job.price) + priceUnitSuffix(job.priceUnit),
                    style = SahyogTheme.typography.labelLarge,
                    color = SahyogTheme.colors.primary,
                )
            }
        }
        if (onAccept != null) {
            Spacer(Modifier.height(SahyogSpacing.md))
            SahyogButton(
                text = stringResource(R.string.worker_accept),
                onClick = onAccept,
                loading = accepting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val AvatarSize = 44.dp
