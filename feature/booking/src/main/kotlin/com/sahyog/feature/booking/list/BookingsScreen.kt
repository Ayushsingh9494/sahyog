package com.sahyog.feature.booking.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogSegmentedControl
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.feature.booking.R
import com.sahyog.feature.booking.component.BookingCard
import com.sahyog.feature.booking.component.bookingMessageRes
import kotlinx.coroutines.launch

@Composable
fun BookingsScreen(
    onBookingClick: (String) -> Unit,
    onFindService: () -> Unit,
    viewModel: BookingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            is BookingsEvent.RefreshFailed -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(event.error.bookingMessageRes()))
            }
        }
    }
    BookingsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onTabSelect = viewModel::onTabSelect,
        onRefresh = viewModel::onRefresh,
        onBookingClick = onBookingClick,
        onFindService = onFindService,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookingsContent(
    uiState: BookingsUiState,
    snackbarHostState: SnackbarHostState,
    onTabSelect: (BookingsTab) -> Unit,
    onRefresh: () -> Unit,
    onBookingClick: (String) -> Unit,
    onFindService: () -> Unit,
) {
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.booking_list_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SahyogSegmentedControl(
                options = BookingsTab.entries,
                selected = uiState.tab,
                onSelect = onTabSelect,
                label = { tab ->
                    stringResource(
                        when (tab) {
                            BookingsTab.Upcoming -> R.string.booking_tab_upcoming
                            BookingsTab.Past -> R.string.booking_tab_past
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.sm),
            )
            if (uiState.isLoading) {
                SahyogLoadingView(Modifier.fillMaxSize())
                return@Column
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = SahyogSpacing.screenGutter,
                        end = SahyogSpacing.screenGutter,
                        top = SahyogSpacing.sm,
                        bottom = SahyogSpacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
                ) {
                    if (uiState.bookings.isEmpty()) {
                        item(key = "empty") {
                            val upcoming = uiState.tab == BookingsTab.Upcoming
                            SahyogEmptyView(
                                title = stringResource(
                                    if (upcoming) R.string.booking_list_empty_upcoming_title else R.string.booking_list_empty_past_title,
                                ),
                                message = stringResource(
                                    if (upcoming) R.string.booking_list_empty_upcoming_message else R.string.booking_list_empty_past_message,
                                ),
                                icon = SahyogIcons.Bookings,
                                actionLabel = if (upcoming) stringResource(R.string.booking_list_find_service) else null,
                                onAction = if (upcoming) onFindService else null,
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }
                    items(uiState.bookings, key = { it.id }) { booking ->
                        BookingCard(booking = booking, today = uiState.today, onClick = { onBookingClick(booking.id) })
                    }
                }
            }
        }
    }
}
