package com.sahyog.feature.booking.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogOutlinedButton
import com.sahyog.core.designsystem.component.SahyogSuccessCheck
import com.sahyog.core.designsystem.component.animatedCount
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.Booking
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PaymentMethod
import com.sahyog.core.ui.catalog.formatRupees
import com.sahyog.feature.booking.R

/**
 * Payment, once the job is done. Due: pay online (the gateway's checkout) or a reminder to pay
 * the worker in cash. Paid: a confirmation, celebrated with an animated tick the first time.
 */
@Composable
internal fun PaymentCard(booking: Booking, payment: PaymentUi, onPayOnline: () -> Unit) {
    AnimatedContent(
        targetState = booking.payment.isPaid,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
        label = "paymentCard",
    ) { paid ->
        if (paid) PaidCard(booking, celebrate = payment.justPaid) else DueCard(booking, payment, onPayOnline)
    }
}

@Composable
private fun DueCard(booking: Booking, payment: PaymentUi, onPayOnline: () -> Unit) {
    val amount = formatRupees(booking.price)
    val online = booking.payment.method == PaymentMethod.Online
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MethodIcon(online)
            Spacer(Modifier.width(SahyogSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.booking_payment_due_title),
                    style = SahyogTheme.typography.labelLarge,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
                Text(
                    text = amount,
                    style = SahyogTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Spacer(Modifier.height(SahyogSpacing.sm))
        Text(
            text = if (online) {
                stringResource(R.string.booking_payment_online_body)
            } else {
                stringResource(R.string.booking_payment_cash_body, amount, booking.worker.name)
            },
            style = SahyogTheme.typography.bodyMedium,
            color = SahyogTheme.colors.onSurfaceVariant,
        )
        if (payment.onlineEnabled) {
            Spacer(Modifier.height(SahyogSpacing.md))
            val busy = payment.step != PaymentStep.Idle
            val label = when (payment.step) {
                PaymentStep.Confirming -> stringResource(R.string.booking_payment_confirming)
                else -> stringResource(R.string.booking_payment_pay_now, amount)
            }
            if (online) {
                SahyogButton(text = label, onClick = onPayOnline, loading = busy, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(SahyogSpacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        SahyogIcons.Lock,
                        contentDescription = null,
                        tint = SahyogTheme.colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(SahyogSpacing.xs))
                    Text(
                        text = stringResource(R.string.booking_payment_secure),
                        style = SahyogTheme.typography.bodySmall,
                        color = SahyogTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                SahyogOutlinedButton(
                    text = if (busy) label else stringResource(R.string.booking_payment_online_instead),
                    onClick = onPayOnline,
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PaidCard(booking: Booking, celebrate: Boolean) {
    // Counts up to the amount when it was just paid; otherwise shows it as is.
    val shown = if (celebrate) Paise(animatedCount(booking.price.value)) else booking.price
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SahyogSuccessCheck(
                size = 56.dp,
                color = SahyogTheme.extendedColors.success,
                animate = celebrate,
            )
            Spacer(Modifier.width(SahyogSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xxs)) {
                Text(
                    text = stringResource(R.string.booking_payment_paid, formatRupees(shown)),
                    style = SahyogTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = when (booking.payment.via) {
                        PaymentMethod.Online -> stringResource(R.string.booking_payment_paid_online)
                        else -> stringResource(R.string.booking_payment_paid_cash, booking.worker.name)
                    },
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MethodIcon(online: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .background(SahyogTheme.colors.primaryContainer, CircleShape)
            .padding(SahyogSpacing.xs),
    ) {
        Icon(
            if (online) SahyogIcons.PayOnline else SahyogIcons.Cash,
            contentDescription = null,
            tint = SahyogTheme.colors.onPrimaryContainer,
        )
    }
}

/** "Cash after the job" / "Online after the job", for the booking's details. */
@Composable
internal fun paymentMethodLabel(method: PaymentMethod): String = stringResource(
    when (method) {
        PaymentMethod.Cash -> R.string.booking_payment_method_cash
        PaymentMethod.Online -> R.string.booking_payment_method_online
    },
)
