package com.sahyog.app.ui.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sahyog.app.R
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.User
import com.sahyog.core.model.WorkerVerificationStatus

/*
 * Temporary tab content so the navigation shell can be used end to end.
 * Each one is replaced by a real feature module: customer discovery (M5),
 * bookings (M6) and the worker side (M7).
 */

@Composable
internal fun PlaceholderTab(
    @StringRes titleRes: Int,
    @StringRes emptyTitleRes: Int,
    @StringRes emptyMessageRes: Int,
    icon: ImageVector,
    header: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(titleRes)) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (header != null) {
                Column(Modifier.padding(horizontal = SahyogSpacing.screenGutter)) { header() }
            }
            SahyogEmptyView(
                title = stringResource(emptyTitleRes),
                message = stringResource(emptyMessageRes),
                icon = icon,
            )
        }
    }
}

@Composable
internal fun CustomerHomePlaceholder(user: User) {
    PlaceholderTab(
        titleRes = R.string.tab_home,
        emptyTitleRes = R.string.placeholder_home_title,
        emptyMessageRes = R.string.placeholder_home_message,
        icon = SahyogIcons.Service,
        header = {
            Text(
                text = stringResource(R.string.home_greeting, user.firstName()),
                style = SahyogTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        },
    )
}

@Composable
internal fun WorkerJobsPlaceholder(user: User) {
    val status = user.workerStatus ?: WorkerVerificationStatus.NotSubmitted
    PlaceholderTab(
        titleRes = R.string.tab_jobs,
        emptyTitleRes = R.string.placeholder_jobs_title,
        emptyMessageRes = R.string.placeholder_jobs_message,
        icon = SahyogIcons.Jobs,
        header = if (status == WorkerVerificationStatus.Verified) {
            null
        } else {
            { VerificationBanner(status) }
        },
    )
}

/** Unverified workers can use the app but won't receive jobs until the cooperative verifies them. */
@Composable
private fun VerificationBanner(status: WorkerVerificationStatus) {
    val (badgeRes, tone, bodyRes) = when (status) {
        WorkerVerificationStatus.NotSubmitted ->
            Triple(R.string.verification_not_submitted, StatusTone.Warning, R.string.verification_not_submitted_body)
        WorkerVerificationStatus.Pending ->
            Triple(R.string.verification_pending, StatusTone.Info, R.string.verification_pending_body)
        WorkerVerificationStatus.Rejected ->
            Triple(R.string.verification_rejected, StatusTone.Error, R.string.verification_rejected_body)
        WorkerVerificationStatus.Verified -> return
    }
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        SahyogStatusBadge(text = stringResource(badgeRes), tone = tone, icon = SahyogIcons.Info)
        Spacer(Modifier.height(SahyogSpacing.sm))
        Text(stringResource(bodyRes), style = SahyogTheme.typography.bodyMedium)
    }
}

private fun User.firstName(): String = name?.trim()?.substringBefore(' ').orEmpty()
