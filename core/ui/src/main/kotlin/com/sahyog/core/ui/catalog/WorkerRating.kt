package com.sahyog.core.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sahyog.core.designsystem.component.SahyogRatingSummary
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.model.Worker
import com.sahyog.core.ui.R

/** Star rating with review count, or a "New" badge for a worker nobody has reviewed yet (never "0.0"). */
@Composable
fun WorkerRating(worker: Worker, modifier: Modifier = Modifier) {
    if (worker.ratingCount == 0) {
        SahyogStatusBadge(text = stringResource(R.string.core_ui_worker_new), tone = StatusTone.Brand, modifier = modifier)
    } else {
        SahyogRatingSummary(rating = worker.rating, reviewCount = worker.ratingCount, modifier = modifier)
    }
}
