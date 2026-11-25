package com.sahyog.feature.booking.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogOutlinedButton
import com.sahyog.core.designsystem.component.SahyogRatingBar
import com.sahyog.core.designsystem.component.SahyogRatingInput
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.Booking
import com.sahyog.core.model.BookingReview
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.ReviewInput
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.core.ui.booking.bookingStatusLabel
import com.sahyog.core.ui.booking.bookingStatusTone
import com.sahyog.core.ui.catalog.formatRupees
import com.sahyog.core.ui.catalog.priceUnitSuffix
import com.sahyog.feature.booking.R
import com.sahyog.feature.booking.component.bookingMessageRes
import com.sahyog.feature.booking.component.bookingWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How often an open booking is re-checked while on screen (until push notifications, M11). */
private const val AUTO_REFRESH_MS = 15_000L

@Composable
fun BookingDetailScreen(
    viewModel: BookingDetailViewModel,
    onNavigateBack: () -> Unit,
    onWorkerClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        val message = when (event) {
            BookingDetailEvent.Cancelled -> resources.getString(R.string.booking_cancelled_message)
            BookingDetailEvent.Reviewed -> resources.getString(R.string.booking_reviewed_message)
            is BookingDetailEvent.Failed -> resources.getString(event.error.bookingMessageRes())
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Poll only while the screen is visible and the booking can still change.
    val isActive = (uiState as? BookingDetailUiState.Ready)?.booking?.status?.isActive == true
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isActive, lifecycleOwner) {
        if (!isActive) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(AUTO_REFRESH_MS)
                viewModel.onAutoRefresh()
            }
        }
    }

    BookingDetailContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::onRefresh,
        onCancel = viewModel::onCancel,
        onWorkerClick = onWorkerClick,
        reviewActions = ReviewActions(
            onRatingChange = viewModel::onReviewRatingChange,
            onCommentChange = viewModel::onReviewCommentChange,
            onSubmit = viewModel::onSubmitReview,
        ),
    )
}

/** Callbacks of the rating card, grouped to keep the content signature short. */
internal data class ReviewActions(
    val onRatingChange: (Int) -> Unit = {},
    val onCommentChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookingDetailContent(
    uiState: BookingDetailUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
    onWorkerClick: (String) -> Unit,
    reviewActions: ReviewActions = ReviewActions(),
) {
    val ready = uiState as? BookingDetailUiState.Ready
    Scaffold(
        topBar = {
            SahyogTopAppBar(
                title = ready?.let { stringResource(R.string.booking_detail_title, it.booking.id) }.orEmpty(),
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            BookingDetailUiState.Loading -> SahyogLoadingView(modifier)
            BookingDetailUiState.NotFound -> SahyogEmptyView(
                title = stringResource(com.sahyog.core.ui.R.string.core_ui_error_not_found),
                modifier = modifier,
            )
            is BookingDetailUiState.Ready -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier,
            ) {
                BookingDetail(uiState, onCancel, onWorkerClick, reviewActions)
            }
        }
    }
}

@Composable
private fun BookingDetail(
    state: BookingDetailUiState.Ready,
    onCancel: () -> Unit,
    onWorkerClick: (String) -> Unit,
    reviewActions: ReviewActions,
) {
    val booking = state.booking
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            top = SahyogSpacing.sm,
            bottom = SahyogSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.lg),
    ) {
        item(key = "status") { StatusCard(booking) }
        val review = booking.review
        if (review != null) {
            item(key = "review") { YourReviewCard(review) }
        } else if (state.canReview) {
            item(key = "review") { ReviewCard(booking.worker.name, state.review, reviewActions) }
        }
        val code = booking.startCode
        if (code != null && booking.status in CODE_VISIBLE) {
            item(key = "code") { StartCodeCard(code, booking.worker.name) }
        }
        if (booking.status in TIMELINE) {
            item(key = "timeline") {
                Section(stringResource(R.string.booking_detail_progress)) { Timeline(booking.status) }
            }
        }
        item(key = "details") { DetailsSection(booking, state.today, onWorkerClick) }
        if (booking.status.canCancel) {
            item(key = "cancel") {
                SahyogOutlinedButton(
                    text = stringResource(R.string.booking_cancel),
                    onClick = { confirmCancel = true },
                    loading = state.isCancelling,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.booking_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.booking_cancel_confirm_body, booking.worker.name)) },
            confirmButton = {
                SahyogTextButton(
                    text = stringResource(R.string.booking_cancel),
                    onClick = {
                        confirmCancel = false
                        onCancel()
                    },
                )
            },
            dismissButton = {
                SahyogTextButton(text = stringResource(R.string.booking_cancel_keep), onClick = { confirmCancel = false })
            },
        )
    }
}

@Composable
private fun ReviewCard(workerName: String, draft: ReviewDraft, actions: ReviewActions) {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.booking_review_title, workerName),
            style = SahyogTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(SahyogSpacing.xs))
        Text(
            text = stringResource(R.string.booking_review_body),
            style = SahyogTheme.typography.bodyMedium,
            color = SahyogTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(SahyogSpacing.sm))
        SahyogRatingInput(
            rating = draft.rating,
            onRatingChange = actions.onRatingChange,
            enabled = !draft.isSubmitting,
            // The stars' touch targets are wider than the icons; line the first icon up with the text.
            modifier = Modifier.offset(x = -SahyogSpacing.sm),
        )
        if (draft.rating == 0) {
            Text(
                text = stringResource(R.string.booking_review_pick_stars),
                style = SahyogTheme.typography.bodySmall,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(SahyogSpacing.sm))
            SahyogTextField(
                value = draft.comment,
                onValueChange = actions.onCommentChange,
                label = stringResource(R.string.booking_review_comment_label),
                singleLine = false,
                enabled = !draft.isSubmitting,
                supportingText = stringResource(
                    R.string.booking_review_comment_counter,
                    draft.comment.length,
                    ReviewInput.COMMENT_MAX_LENGTH,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SahyogSpacing.md))
            SahyogButton(
                text = stringResource(R.string.booking_review_submit),
                onClick = actions.onSubmit,
                enabled = draft.canSubmit,
                loading = draft.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SahyogSpacing.xs))
            Text(
                text = stringResource(R.string.booking_review_final),
                style = SahyogTheme.typography.bodySmall,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YourReviewCard(review: BookingReview) {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.booking_review_yours),
            style = SahyogTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(SahyogSpacing.xs))
        SahyogRatingBar(rating = review.rating.toFloat(), starSize = ReviewStar)
        review.comment?.let {
            Spacer(Modifier.height(SahyogSpacing.xs))
            Text(it, style = SahyogTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusCard(booking: Booking) {
    val name = booking.worker.name
    val title = when (booking.status) {
        BookingStatus.Requested -> stringResource(R.string.booking_detail_requested, name)
        BookingStatus.Accepted -> stringResource(R.string.booking_detail_accepted, name)
        BookingStatus.WorkerEnRoute -> stringResource(R.string.booking_detail_en_route, name)
        BookingStatus.InProgress -> stringResource(R.string.booking_detail_in_progress)
        BookingStatus.Completed -> stringResource(R.string.booking_detail_completed)
        BookingStatus.Cancelled -> stringResource(R.string.booking_detail_cancelled)
        BookingStatus.Rejected -> stringResource(R.string.booking_detail_rejected, name)
        BookingStatus.Expired -> stringResource(R.string.booking_detail_expired)
    }
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        SahyogStatusBadge(text = bookingStatusLabel(booking.status), tone = bookingStatusTone(booking.status))
        Spacer(Modifier.height(SahyogSpacing.sm))
        Text(title, style = SahyogTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (booking.status == BookingStatus.Requested) {
            Spacer(Modifier.height(SahyogSpacing.xs))
            Text(
                text = stringResource(R.string.booking_detail_requested_body),
                style = SahyogTheme.typography.bodyMedium,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StartCodeCard(code: String, workerName: String) {
    val description = stringResource(R.string.booking_start_code_description, code.toCharArray().joinToString(" "))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SahyogTheme.colors.primaryContainer, SahyogTheme.shapes.large)
            .padding(SahyogSpacing.lg),
    ) {
        Column {
            Text(
                text = stringResource(R.string.booking_start_code),
                style = SahyogTheme.typography.labelLarge,
                color = SahyogTheme.colors.onPrimaryContainer,
            )
            Text(
                text = code.toCharArray().joinToString(" "),
                style = SahyogTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                color = SahyogTheme.colors.onPrimaryContainer,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            )
            Spacer(Modifier.height(SahyogSpacing.xs))
            Text(
                text = stringResource(R.string.booking_start_code_body, workerName),
                style = SahyogTheme.typography.bodyMedium,
                color = SahyogTheme.colors.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun Timeline(status: BookingStatus) {
    val steps = listOf(
        BookingStatus.Requested to R.string.booking_step_requested,
        BookingStatus.Accepted to R.string.booking_step_accepted,
        BookingStatus.WorkerEnRoute to R.string.booking_step_en_route,
        BookingStatus.InProgress to R.string.booking_step_in_progress,
        BookingStatus.Completed to R.string.booking_step_completed,
    )
    val current = steps.indexOfFirst { it.first == status }
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, (_, labelRes) ->
            val done = index <= current
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = SahyogSpacing.xs)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(StepDot)
                        .background(
                            if (done) SahyogTheme.colors.primary else SahyogTheme.colors.surfaceContainerHighest,
                            CircleShape,
                        ),
                ) {
                    if (done) {
                        Icon(
                            SahyogIcons.Verified,
                            contentDescription = null,
                            tint = SahyogTheme.colors.onPrimary,
                            modifier = Modifier.size(StepIcon),
                        )
                    }
                }
                Spacer(Modifier.width(SahyogSpacing.md))
                Text(
                    text = stringResource(labelRes),
                    style = if (index == current) SahyogTheme.typography.titleSmall else SahyogTheme.typography.bodyMedium,
                    color = if (done) SahyogTheme.colors.onSurface else SahyogTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailsSection(booking: Booking, today: LocalDate, onWorkerClick: (String) -> Unit) {
    Section(stringResource(R.string.booking_detail_details)) {
        SahyogCard(modifier = Modifier.fillMaxWidth(), onClick = { onWorkerClick(booking.worker.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SahyogAvatar(name = booking.worker.name, size = AvatarSize)
                Spacer(Modifier.width(SahyogSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(booking.worker.name, style = SahyogTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.booking_detail_view_profile),
                        style = SahyogTheme.typography.bodyMedium,
                        color = SahyogTheme.colors.primary,
                    )
                }
                Icon(SahyogIcons.Forward, contentDescription = null, tint = SahyogTheme.colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(SahyogSpacing.md))
        SahyogCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md)) {
                DetailRow(stringResource(R.string.booking_detail_service), booking.serviceName)
                DetailRow(
                    stringResource(R.string.booking_detail_price),
                    formatRupees(booking.price) + priceUnitSuffix(booking.priceUnit),
                )
                DetailRow(stringResource(R.string.booking_detail_when), bookingWhen(booking, today))
                DetailRow(stringResource(R.string.booking_detail_address), booking.address)
                if (booking.location != null) {
                    DetailRow(stringResource(R.string.booking_detail_pin), stringResource(R.string.booking_detail_pin_value))
                }
                booking.note?.let { DetailRow(stringResource(R.string.booking_detail_note), it) }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = SahyogTheme.typography.bodyMedium,
            color = SahyogTheme.colors.onSurfaceVariant,
            modifier = Modifier.width(DetailLabelWidth),
        )
        Text(value, style = SahyogTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = SahyogTheme.typography.titleMedium,
            modifier = Modifier
                .padding(bottom = SahyogSpacing.sm)
                .semantics { heading() },
        )
        content()
    }
}

private val CODE_VISIBLE = setOf(BookingStatus.Accepted, BookingStatus.WorkerEnRoute)
private val TIMELINE = setOf(
    BookingStatus.Requested,
    BookingStatus.Accepted,
    BookingStatus.WorkerEnRoute,
    BookingStatus.InProgress,
    BookingStatus.Completed,
)
private val StepDot = 24.dp
private val ReviewStar = 20.dp
private val StepIcon = 16.dp
private val AvatarSize = 44.dp
private val DetailLabelWidth = 88.dp
