package com.sahyog.feature.worker.schedule

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
                        JobCard(job = job, today = uiState.today, onClick = { onJobClick(job.id) })
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

@Composable
private fun TotalCard(labelRes: Int, amount: Paise, count: Int, modifier: Modifier) {
    SahyogCard(modifier = modifier) {
        Column {
            Text(
                text = stringResource(labelRes),
                style = SahyogTheme.typography.labelLarge,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
            Text(text = formatRupees(amount), style = SahyogTheme.typography.headlineSmall)
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
