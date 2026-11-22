package com.sahyog.core.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSize
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.Worker
import com.sahyog.core.model.WorkerService
import com.sahyog.core.ui.R

/**
 * A worker in a list: who they are, what they do, how they're rated, where they work
 * and what they charge. Shared by Home, Category and Search (and later Bookings).
 */
@Composable
fun WorkerCard(
    worker: Worker,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahyogCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            SahyogAvatar(name = worker.name, size = AvatarSize)
            Spacer(Modifier.width(SahyogSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xxs)) {
                Text(
                    text = worker.name,
                    style = SahyogTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = worker.categories.map { categoryLabel(it) }.joinToString(" · "),
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                WorkerRating(worker)
            }
            if (worker.isAvailable) {
                Spacer(Modifier.width(SahyogSpacing.sm))
                SahyogStatusBadge(text = stringResource(R.string.core_ui_worker_available), tone = StatusTone.Success)
            }
        }
        Spacer(Modifier.size(SahyogSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                SahyogIcons.Location,
                contentDescription = null,
                tint = SahyogTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(SahyogSize.iconSmall),
            )
            Spacer(Modifier.width(SahyogSpacing.xs))
            Text(
                text = worker.locality,
                style = SahyogTheme.typography.bodySmall,
                color = SahyogTheme.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            worker.startingPrice?.let { price ->
                Text(
                    text = stringResource(R.string.core_ui_price_from, formatRupees(price)),
                    style = SahyogTheme.typography.labelLarge,
                    color = SahyogTheme.colors.primary,
                )
            }
        }
    }
}

private val AvatarSize = 52.dp

@Preview
@Composable
private fun WorkerCardPreview() {
    SahyogTheme {
        WorkerCard(
            worker = Worker(
                id = "1",
                name = "Ramesh Kumar",
                categories = listOf(ServiceCategory("electrician", "Electrician")),
                rating = 4.8f,
                ratingCount = 212,
                jobsCompleted = 486,
                yearsExperience = 12,
                locality = "Model Town",
                about = "",
                languages = listOf("Hindi"),
                services = listOf(WorkerService("Fan installation", Paise.rupees(199), PriceUnit.PerVisit)),
                isAvailable = true,
            ),
            onClick = {},
        )
    }
}
