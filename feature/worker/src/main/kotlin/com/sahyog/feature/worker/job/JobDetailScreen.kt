package com.sahyog.feature.worker.job

import androidx.compose.ui.Alignment
import com.sahyog.core.model.PaymentMethod
import com.sahyog.core.designsystem.component.SahyogSuccessCheck
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.model.GeoPoint
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import android.widget.Toast
import android.content.Intent
import android.content.Context
import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.data.worker.WorkerErrorFields
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogOutlinedButton
import com.sahyog.core.designsystem.component.SahyogRatingBar
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.theme.SahyogElevation
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.Job
import com.sahyog.core.model.JobAction
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.core.ui.booking.bookingStatusLabel
import com.sahyog.core.ui.booking.bookingStatusTone
import com.sahyog.core.ui.catalog.formatRupees
import com.sahyog.core.ui.catalog.priceUnitSuffix
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.worker.R
import com.sahyog.feature.worker.component.customerName
import com.sahyog.feature.worker.component.jobWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AUTO_REFRESH_MS = 15_000L

/** Which confirmation is open. Saved across rotation. */
private enum class JobDialog { None, Decline, Code, Complete, Cash }

@Composable
fun JobDetailScreen(
    viewModel: JobDetailViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var dialog by rememberSaveable { mutableStateOf(JobDialog.None) }
    var codeError by rememberSaveable { mutableStateOf(false) }

    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            is JobDetailEvent.Done -> {
                dialog = JobDialog.None
                scope.launch { snackbarHostState.showSnackbar(resources.getString(event.action.doneRes())) }
            }
            is JobDetailEvent.Failed -> {
                val wrongCode = event.error.let { it is AppError.Validation && it.field == WorkerErrorFields.START_CODE }
                if (wrongCode) {
                    codeError = true // stays in the dialog so the worker can retry
                } else {
                    dialog = JobDialog.None
                    scope.launch { snackbarHostState.showSnackbar(resources.getString(event.error.jobMessageRes())) }
                }
            }
        }
    }

    val isOpen = (uiState as? JobDetailUiState.Ready)?.job?.status?.isActive == true
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isOpen, lifecycleOwner) {
        if (!isOpen) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(AUTO_REFRESH_MS)
                viewModel.onAutoRefresh()
            }
        }
    }

    val ready = uiState as? JobDetailUiState.Ready
    Scaffold(
        topBar = {
            SahyogTopAppBar(
                title = ready?.let { stringResource(R.string.worker_job_title, it.job.id) }.orEmpty(),
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            if (ready != null && ready.actions.isNotEmpty()) {
                ActionBar(
                    state = ready,
                    onAccept = viewModel::onAccept,
                    onDecline = { dialog = JobDialog.Decline },
                    onStartTrip = viewModel::onStartTrip,
                    onStartJob = {
                        codeError = false
                        dialog = JobDialog.Code
                    },
                    onComplete = { dialog = JobDialog.Complete },
                    onCashReceived = { dialog = JobDialog.Cash },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val state = uiState) {
            JobDetailUiState.Loading -> SahyogLoadingView(modifier)
            JobDetailUiState.NotFound -> SahyogEmptyView(
                title = stringResource(com.sahyog.core.ui.R.string.core_ui_error_not_found),
                modifier = modifier,
            )
            is JobDetailUiState.Ready -> JobDetail(state, modifier)
        }
    }

    if (ready != null) {
        val name = customerName(ready.job)
        when (dialog) {
            JobDialog.None -> Unit
            JobDialog.Decline -> ConfirmDialog(
                title = stringResource(R.string.worker_decline_confirm_title),
                body = stringResource(R.string.worker_decline_confirm_body, name),
                confirmLabel = stringResource(R.string.worker_job_decline),
                onConfirm = viewModel::onDecline,
                onDismiss = { dialog = JobDialog.None },
            )
            JobDialog.Complete -> ConfirmDialog(
                title = stringResource(R.string.worker_complete_confirm_title),
                body = stringResource(R.string.worker_complete_confirm_body),
                confirmLabel = stringResource(R.string.worker_job_complete),
                onConfirm = viewModel::onComplete,
                onDismiss = { dialog = JobDialog.None },
            )
            JobDialog.Cash -> ConfirmDialog(
                title = stringResource(R.string.worker_cash_confirm_title),
                body = stringResource(R.string.worker_cash_confirm_body, name, formatRupees(ready.job.price)),
                confirmLabel = stringResource(R.string.worker_job_cash_received),
                onConfirm = viewModel::onCashReceived,
                onDismiss = { dialog = JobDialog.None },
            )
            JobDialog.Code -> StartCodeDialog(
                showError = codeError,
                submitting = ready.actionInFlight == JobAction.StartJob,
                onCodeChange = { codeError = false },
                onSubmit = viewModel::onStartJob,
                onDismiss = { dialog = JobDialog.None },
            )
        }
    }
}

@Composable
private fun JobDetail(state: JobDetailUiState.Ready, modifier: Modifier) {
    val job = state.job
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            top = SahyogSpacing.sm,
            bottom = SahyogSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.lg),
    ) {
        item(key = "status") {
            SahyogCard(modifier = Modifier.fillMaxWidth().animateItem()) {
                SahyogStatusBadge(text = bookingStatusLabel(job.status), tone = bookingStatusTone(job.status))
                Spacer(Modifier.height(SahyogSpacing.sm))
                Text(
                    text = statusTitle(job),
                    style = SahyogTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                if (job.status == BookingStatus.Completed) {
                    Spacer(Modifier.height(SahyogSpacing.xs))
                    Text(
                        text = stringResource(R.string.worker_job_earned, formatRupees(job.price)),
                        style = SahyogTheme.typography.bodyLarge,
                        color = SahyogTheme.colors.primary,
                    )
                }
            }
        }
        if (job.status == BookingStatus.Completed) {
            item(key = "payment") { JobPaymentCard(job, Modifier.animateItem()) }
        }
        job.review?.let { review ->
            item(key = "review") {
                SahyogCard(modifier = Modifier.fillMaxWidth().animateItem()) {
                    Text(
                        text = stringResource(R.string.worker_job_review_title),
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
        }
        item(key = "details") {
            SahyogCard(modifier = Modifier.fillMaxWidth().animateItem()) {
                Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md)) {
                    DetailRow(stringResource(R.string.worker_job_customer), customerName(job))
                    DetailRow(stringResource(R.string.worker_job_service), job.serviceName)
                    DetailRow(
                        stringResource(R.string.worker_job_price),
                        formatRupees(job.price) + priceUnitSuffix(job.priceUnit),
                    )
                    DetailRow(stringResource(R.string.worker_job_when), jobWhen(job, state.today))
                    DetailRow(
                        stringResource(R.string.worker_job_payment),
                        stringResource(
                            if (job.payment.method == PaymentMethod.Online) R.string.worker_job_payment_online else R.string.worker_job_payment_cash,
                        ),
                    )
                    DetailRow(stringResource(R.string.worker_job_address), job.address)
                    job.note?.let { DetailRow(stringResource(R.string.worker_job_note), it) }
                }
                val pin = job.location
                if (pin != null && job.status.isActive) {
                    Spacer(Modifier.height(SahyogSpacing.md))
                    val context = LocalContext.current
                    SahyogOutlinedButton(
                        text = stringResource(R.string.worker_job_directions),
                        onClick = { context.openDirections(pin) },
                        leadingIcon = SahyogIcons.Location,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun statusTitle(job: Job): String {
    val name = customerName(job)
    return when (job.status) {
        BookingStatus.Requested -> stringResource(R.string.worker_job_requested, name)
        BookingStatus.Accepted -> stringResource(R.string.worker_job_accepted)
        BookingStatus.WorkerEnRoute -> stringResource(R.string.worker_job_en_route, name)
        BookingStatus.InProgress -> stringResource(R.string.worker_job_in_progress)
        BookingStatus.Completed -> stringResource(R.string.worker_job_completed)
        BookingStatus.Cancelled -> stringResource(R.string.worker_job_cancelled)
        BookingStatus.Rejected -> stringResource(R.string.worker_job_rejected)
        BookingStatus.Expired -> stringResource(R.string.worker_job_expired)
    }
}

@Composable
private fun ActionBar(
    state: JobDetailUiState.Ready,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onStartTrip: () -> Unit,
    onStartJob: () -> Unit,
    onComplete: () -> Unit,
    onCashReceived: () -> Unit,
) {
    val busy = state.actionInFlight
    Surface(color = SahyogTheme.colors.surfaceContainer, tonalElevation = SahyogElevation.medium) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.md),
        ) {
            val actions = state.actions
            if (JobAction.Decline in actions) {
                SahyogOutlinedButton(
                    text = stringResource(R.string.worker_job_decline),
                    onClick = onDecline,
                    enabled = busy == null,
                    loading = busy == JobAction.Decline,
                    modifier = Modifier.weight(1f),
                )
            }
            val (labelRes, action, onClick) = when {
                JobAction.Accept in actions -> Triple(R.string.worker_accept, JobAction.Accept, onAccept)
                JobAction.StartTrip in actions -> Triple(R.string.worker_job_start_trip, JobAction.StartTrip, onStartTrip)
                JobAction.StartJob in actions -> Triple(R.string.worker_job_start_job, JobAction.StartJob, onStartJob)
                JobAction.Complete in actions -> Triple(R.string.worker_job_complete, JobAction.Complete, onComplete)
                JobAction.CashReceived in actions ->
                    Triple(R.string.worker_job_cash_received, JobAction.CashReceived, onCashReceived)
                else -> return@Row
            }
            SahyogButton(
                text = stringResource(labelRes),
                onClick = onClick,
                enabled = busy == null,
                loading = busy == action,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StartCodeDialog(
    showError: Boolean,
    submitting: Boolean,
    onCodeChange: () -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.worker_code_title)) },
        text = {
            Column {
                Text(stringResource(R.string.worker_code_body))
                Spacer(Modifier.height(SahyogSpacing.md))
                SahyogTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter(Char::isDigit).take(CODE_LENGTH)
                        onCodeChange()
                    },
                    label = stringResource(R.string.worker_code_label),
                    errorText = stringResource(R.string.worker_code_error).takeIf { showError },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            SahyogButton(
                text = stringResource(R.string.worker_job_start_job),
                onClick = { onSubmit(code) },
                enabled = code.length == CODE_LENGTH && !submitting,
                loading = submitting,
            )
        },
        dismissButton = { SahyogTextButton(text = stringResource(R.string.worker_cancel), onClick = onDismiss) },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { SahyogTextButton(text = confirmLabel, onClick = onConfirm) },
        dismissButton = { SahyogTextButton(text = stringResource(R.string.worker_cancel), onClick = onDismiss) },
    )
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

private fun JobAction.doneRes(): Int = when (this) {
    JobAction.Accept -> R.string.worker_done_accept
    JobAction.Decline -> R.string.worker_done_decline
    JobAction.StartTrip -> R.string.worker_done_trip
    JobAction.StartJob -> R.string.worker_done_start
    JobAction.Complete -> R.string.worker_done_complete
    JobAction.CashReceived -> R.string.worker_done_cash
}

/**
 * What's owed for a completed job, or the receipt once paid. The tick animates only when
 * the payment arrives while the worker is looking at the job.
 */
@Composable
private fun JobPaymentCard(job: Job, modifier: Modifier = Modifier) {
    val paidWhenOpened = remember { job.payment.isPaid }
    val amount = formatRupees(job.price)
    val name = customerName(job)
    SahyogCard(modifier = modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = job.payment.isPaid,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "jobPayment",
        ) { paid ->
            if (paid) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SahyogSuccessCheck(size = 48.dp, color = SahyogTheme.extendedColors.success, animate = !paidWhenOpened)
                    Spacer(Modifier.width(SahyogSpacing.md))
                    Column {
                        Text(
                            text = stringResource(R.string.worker_job_paid, amount),
                            style = SahyogTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(
                                if (job.payment.via == PaymentMethod.Online) R.string.worker_job_paid_online else R.string.worker_job_paid_cash,
                            ),
                            style = SahyogTheme.typography.bodyMedium,
                            color = SahyogTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column {
                    Text(
                        text = stringResource(R.string.worker_job_to_collect, amount),
                        style = SahyogTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(SahyogSpacing.xs))
                    Text(
                        text = if (job.payment.method == PaymentMethod.Online) {
                            stringResource(R.string.worker_job_collect_online, name)
                        } else {
                            stringResource(R.string.worker_job_collect_cash, amount, name)
                        },
                        style = SahyogTheme.typography.bodyMedium,
                        color = SahyogTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun AppError.jobMessageRes(): Int = when {
    this is AppError.Validation && field == BookingErrorFields.STATUS -> R.string.worker_job_status_changed
    this is AppError.Validation && field == WorkerErrorFields.TOO_EARLY -> R.string.worker_job_too_early
    else -> messageRes()
}

private const val CODE_LENGTH = 4
private val DetailLabelWidth = 88.dp

private val ReviewStar = 20.dp

/**
 * Opens turn-by-turn directions to the customer's pin in Google Maps (or the browser if
 * the app isn't installed). The universal Maps URL needs no API key.
 */
private fun Context.openDirections(destination: GeoPoint) {
    val uri = "https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}".toUri()
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.worker_job_directions_unavailable, Toast.LENGTH_SHORT).show()
    }
}
