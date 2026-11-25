package com.sahyog.feature.admin.component

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogFilterChip
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.AdminReason
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.StatusChange
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.ui.catalog.categoryLabel
import com.sahyog.feature.admin.R
import java.time.Instant

// --- Labels and tones ---------------------------------------------------------------------

@StringRes
internal fun WorkerVerificationStatus.labelRes(): Int = when (this) {
    WorkerVerificationStatus.Pending -> R.string.admin_status_pending
    WorkerVerificationStatus.Verified -> R.string.admin_status_verified
    WorkerVerificationStatus.Rejected -> R.string.admin_status_rejected
    WorkerVerificationStatus.NotSubmitted -> R.string.admin_status_not_submitted
}

internal fun WorkerVerificationStatus.tone(): StatusTone = when (this) {
    WorkerVerificationStatus.Pending -> StatusTone.Warning
    WorkerVerificationStatus.Verified -> StatusTone.Success
    WorkerVerificationStatus.Rejected -> StatusTone.Error
    WorkerVerificationStatus.NotSubmitted -> StatusTone.Neutral
}

@Composable
internal fun StatusBadge(status: WorkerVerificationStatus, modifier: Modifier = Modifier) =
    SahyogStatusBadge(stringResource(status.labelRes()), status.tone(), modifier)

@Composable
internal fun documentLabel(type: IdDocumentType): String = stringResource(
    when (type) {
        IdDocumentType.Aadhaar -> R.string.admin_doc_aadhaar
        IdDocumentType.VoterId -> R.string.admin_doc_voter_id
        IdDocumentType.DrivingLicence -> R.string.admin_doc_driving_licence
        IdDocumentType.Pan -> R.string.admin_doc_pan
    },
)

/** "Electrician · Plumber" from category ids (translated where the app knows them). */
@Composable
internal fun categoriesLabel(ids: List<String>): String =
    ids.map { categoryLabel(ServiceCategory(it, it.replace('_', ' ').replaceFirstChar(Char::uppercase))) }
        .joinToString(" · ")

/** "3 hours ago", "Yesterday": relative, in the phone's language. */
internal fun relativeTime(then: Instant, now: Instant): String =
    DateUtils.getRelativeTimeSpanString(then.toEpochMilli(), now.toEpochMilli(), DateUtils.MINUTE_IN_MILLIS).toString()

@StringRes
internal fun AdminReason.labelRes(): Int = when (this) {
    AdminReason.PhotoUnclear -> R.string.admin_reason_PhotoUnclear
    AdminReason.NameMismatch -> R.string.admin_reason_NameMismatch
    AdminReason.DocumentNotAccepted -> R.string.admin_reason_DocumentNotAccepted
    AdminReason.DocumentExpired -> R.string.admin_reason_DocumentExpired
    AdminReason.Complaints -> R.string.admin_reason_Complaints
    AdminReason.Other -> R.string.admin_reason_Other
}

// --- Building blocks ----------------------------------------------------------------------

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = SahyogTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action?.invoke()
    }
}

/** A dashboard number: big value, small label, a coloured accent dot for the tone. */
@Composable
internal fun StatTile(value: String, label: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val accent = when (tone) {
        StatusTone.Success -> SahyogTheme.extendedColors.success
        StatusTone.Warning -> SahyogTheme.extendedColors.warning
        StatusTone.Error -> SahyogTheme.colors.error
        StatusTone.Info -> SahyogTheme.colors.tertiary
        StatusTone.Brand -> SahyogTheme.colors.primary
        StatusTone.Neutral -> SahyogTheme.colors.outline
    }
    SahyogCard(
        modifier = modifier.semantics(mergeDescendants = true) {},
        contentPadding = PaddingValues(SahyogSpacing.lg),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(accent, CircleShape),
        )
        Spacer(Modifier.height(SahyogSpacing.sm))
        Text(value, style = SahyogTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(
            text = label,
            style = SahyogTheme.typography.bodySmall,
            color = SahyogTheme.colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One audit-trail entry: what happened, to whom, by whom, when and why. */
@Composable
internal fun StatusChangeRow(change: StatusChange, now: Instant, showWorker: Boolean, onClick: (() -> Unit)? = null) {
    val title = when {
        change.to == WorkerVerificationStatus.Verified -> stringResource(R.string.admin_change_approved, change.workerName)
        change.to == WorkerVerificationStatus.Rejected && change.from == WorkerVerificationStatus.Verified ->
            stringResource(R.string.admin_change_revoked, change.workerName)
        change.to == WorkerVerificationStatus.Rejected -> stringResource(R.string.admin_change_rejected, change.workerName)
        change.to == WorkerVerificationStatus.Pending -> stringResource(R.string.admin_change_submitted, change.workerName)
        else -> stringResource(R.string.admin_change_other, change.workerName, stringResource(change.to.labelRes()))
    }
    val whenText = relativeTime(change.changedAt, now)
    val byline = change.changedBy?.let { stringResource(R.string.admin_change_by, it, whenText) }
        ?: stringResource(R.string.admin_change_by_worker, whenText)
    val clickable = if (onClick != null) {
        Modifier
            .clip(SahyogTheme.shapes.medium)
            .clickable(onClick = onClick)
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Row(
        modifier = clickable
            .fillMaxWidth()
            .padding(vertical = SahyogSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        val tone = change.to.tone()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(tone.container(), CircleShape),
        ) {
            Icon(
                imageVector = when (change.to) {
                    WorkerVerificationStatus.Verified -> SahyogIcons.Check
                    WorkerVerificationStatus.Rejected -> SahyogIcons.Close
                    else -> SahyogIcons.Info
                },
                contentDescription = null,
                tint = tone.content(),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(SahyogSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (showWorker) title else stringResource(change.to.labelRes()),
                style = SahyogTheme.typography.titleSmall,
            )
            Text(byline, style = SahyogTheme.typography.bodySmall, color = SahyogTheme.colors.onSurfaceVariant)
            change.reason?.let {
                Spacer(Modifier.height(SahyogSpacing.xxs))
                Text("“$it”", style = SahyogTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusTone.container(): Color = when (this) {
    StatusTone.Success -> SahyogTheme.extendedColors.successContainer
    StatusTone.Warning -> SahyogTheme.extendedColors.warningContainer
    StatusTone.Error -> SahyogTheme.colors.errorContainer
    else -> SahyogTheme.colors.surfaceContainerHighest
}

@Composable
private fun StatusTone.content(): Color = when (this) {
    StatusTone.Success -> SahyogTheme.extendedColors.onSuccessContainer
    StatusTone.Warning -> SahyogTheme.extendedColors.onWarningContainer
    StatusTone.Error -> SahyogTheme.colors.onErrorContainer
    else -> SahyogTheme.colors.onSurfaceVariant
}

/** A person row used in the queue and the directory. */
@Composable
internal fun PersonCard(
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
) {
    SahyogCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SahyogAvatar(name = name, size = 44.dp)
            Spacer(Modifier.width(SahyogSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(name, style = SahyogTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(SahyogSpacing.sm))
            trailing()
        }
        if (footer != null) {
            Spacer(Modifier.height(SahyogSpacing.md))
            footer()
        }
    }
}

// --- Identity document --------------------------------------------------------------------

/**
 * Decodes the ID photo in memory, downscaled so a 12 MP photo doesn't need 48 MB of RAM.
 * Returns null for formats this phone can't decode.
 */
internal fun decodeDocument(bytes: ByteArray, maxSide: Int = 2048): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

/**
 * Blocks screenshots and screen recording (and hides the screen in Recents) while an identity
 * document is visible. Restored when the composable leaves.
 */
@Composable
internal fun SecureScreen() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Full-screen, pinch-to-zoom view of the document on a dark background. */
@Composable
internal fun DocumentViewer(image: ImageBitmap, description: String, onDismiss: () -> Unit) {
    // The dialog is its own window: it needs its own screenshot block.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            Image(
                bitmap = image,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            SahyogTextButton(
                text = stringResource(R.string.admin_close),
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(SahyogSpacing.sm)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape),
            )
        }
    }
}

// --- Reason sheet -------------------------------------------------------------------------

/**
 * Asks why (reject / revoke): quick reason chips plus optional details. The text sent to the
 * server is the chosen reason, then the details.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReasonSheet(
    title: String,
    body: String,
    reasons: List<AdminReason>,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by rememberSaveable { mutableStateOf<AdminReason?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    val chosenLabel = selected?.let { stringResource(it.labelRes()) }
    val reasonText = listOfNotNull(chosenLabel, details.trim().ifBlank { null }).joinToString(": ")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SahyogTheme.colors.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SahyogSpacing.screenGutter)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(title, style = SahyogTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(SahyogSpacing.xs))
            Text(body, style = SahyogTheme.typography.bodyMedium, color = SahyogTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(SahyogSpacing.lg))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm),
            ) {
                reasons.forEach { reason ->
                    SahyogFilterChip(
                        label = stringResource(reason.labelRes()),
                        selected = selected == reason,
                        onClick = { selected = reason },
                    )
                }
            }
            Spacer(Modifier.height(SahyogSpacing.md))
            SahyogTextField(
                value = details,
                onValueChange = { details = it.take(DETAILS_MAX) },
                label = stringResource(R.string.admin_reason_details),
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SahyogSpacing.lg))
            SahyogButton(
                text = confirmLabel,
                onClick = { onConfirm(reasonText) },
                // The server needs at least a few characters of reason.
                enabled = reasonText.length >= MIN_REASON,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SahyogSpacing.lg))
        }
    }
}

private const val DETAILS_MAX = 200
private const val MIN_REASON = 3
