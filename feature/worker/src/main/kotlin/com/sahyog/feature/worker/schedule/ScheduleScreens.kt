package com.sahyog.feature.worker.schedule

import com.sahyog.core.model.EarningsSummary
import com.sahyog.core.designsystem.component.animatedCount
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.Paise
import com.sahyog.core.ui.booking.formatBookingDate
import com.sahyog.core.ui.catalog.formatRupees
import com.sahyog.feature.worker.R
import com.sahyog.feature.worker.component.JobCard

@Composable
fun ScheduleScreen(
    onJobClick: (String) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.worker_schedule_title)) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            uiState.isLoading -> SahyogLoadingView(modifier)
            uiState.days.isEmpty() -> SahyogEmptyView(
                title = stringResource(R.string.worker_schedule_empty_title),
                message = stringResource(R.string.worker_schedule_empty_message),
                icon = SahyogIcons.Bookings,
                modifier = modifier,
            )
            else -> LazyColumn(
                modifier = modifier,
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
            ) {
                uiState.days.forEach { (day, jobs) ->
                    item(key = "day-$day") {
                        Heading(formatBookingDate(day, uiState.today))
                    }
                    items(jobs, key = { it.id }) { job ->
                        JobCard(job = job, today = uiState.today, onClick = { onJobClick(job.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
fun EarningsScreen(
    onJobClick: (String) -> Unit,
    viewModel: EarningsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.worker_earnings_title)) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        val summary = uiState.summary
        when {
            uiState.isLoading || summary == null -> SahyogLoadingView(modifier)
            else -> LazyColumn(
                modifier = modifier,
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
            ) {
                item(key = "totals") {
                    Row(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                        TotalCard(R.string.worker_earnings_week, summary.thisWeek, summary.thisWeekCount, Modifier.weight(1f))
                        TotalCard(R.string.worker_earnings_month, summary.thisMonth, summary.thisMonthCount, Modifier.weight(1f))
                    }
                }
                item(key = "all") {
                    TotalCard(R.string.worker_earnings_all, summary.allTime, summary.allTimeCount, Modifier.fillMaxWidth())
                }
                item(key = "split") { PaymentSplit(summary) }
                item(key = "note") {
                    Text(
                        text = stringResource(R.string.worker_earnings_note),
                        style = SahyogTheme.typography.bodySmall,
                        color = SahyogTheme.colors.onSurfaceVariant,
                    )
                }
                if (uiState.completed.isEmpty()) {
                    item(key = "empty") {
                        SahyogEmptyView(
                            title = stringResource(R.string.worker_earnings_empty_title),
                            message = stringResource(R.string.worker_earnings_empty_message),
                            icon = SahyogIcons.Star,
                        )
                    }
                } else {
                    item(key = "completed-header") { Heading(stringResource(R.string.worker_earnings_completed)) }
                    items(uiState.completed, key = { it.id }) { job ->
                        JobCard(job = job, today = uiState.today, onClick = { onJobClick(job.id) })
                    }
                }
            }
        }
    }
}

/** Received in cash, received online, still to collect: with a bar showing the proportions. */
@Composable
private fun PaymentSplit(summary: EarningsSummary) {
    val total = (summary.receivedCash.value + summary.receivedOnline.value + summary.toCollect.value).coerceAtLeast(1)
    val colors = SahyogTheme.colors
    val parts = listOf(
        Triple(R.string.worker_earnings_cash, summary.receivedCash, colors.primary),
        Triple(R.string.worker_earnings_online, summary.receivedOnline, colors.tertiary),
        Triple(R.string.worker_earnings_to_collect, summary.toCollect, colors.outlineVariant),
    )
    // The bar grows in from the left once.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(durationMillis = 700)) }
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest),
        ) {
            parts.forEach { (_, amount, color) ->
                val share = amount.value.toFloat() / total * grow.value
                if (share > 0f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(share)
                            .background(color),
                    )
                }
            }
            val rest = 1f - grow.value
            if (rest > 0f) Spacer(Modifier.weight(rest))
        }
        Spacer(Modifier.height(SahyogSpacing.md))
        parts.forEach { (labelRes, amount, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = SahyogSpacing.xxs)) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(color, CircleShape),
                )
                Spacer(Modifier.width(SahyogSpacing.sm))
                Text(stringResource(labelRes), style = SahyogTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(formatRupees(amount), style = SahyogTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun TotalCard(labelRes: Int, amount: Paise, count: Int, modifier: Modifier) {
    SahyogCard(modifier = modifier) {
        Column {
            Text(
                text = stringResource(labelRes),
                style = SahyogTheme.typography.labelLarge,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
            // Totals count up when the tab opens.
            Text(text = formatRupees(Paise(animatedCount(amount.value))), style = SahyogTheme.typography.headlineSmall)
            Text(
                text = pluralStringResource(R.plurals.worker_earnings_jobs, count, count),
                style = SahyogTheme.typography.bodySmall,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = SahyogTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = SahyogSpacing.sm)
            .semantics { heading() },
    )
}

private val listPadding = PaddingValues(
    start = SahyogSpacing.screenGutter,
    end = SahyogSpacing.screenGutter,
    top = SahyogSpacing.sm,
    bottom = SahyogSpacing.xxl,
)
