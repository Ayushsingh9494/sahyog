package com.sahyog.feature.discovery.worker

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogRatingBar
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogElevation
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.Worker
import com.sahyog.core.model.WorkerReview
import com.sahyog.core.model.WorkerService
import com.sahyog.core.ui.booking.formatBookingDate
import com.sahyog.core.ui.catalog.WorkerRating
import com.sahyog.core.ui.catalog.categoryLabel
import com.sahyog.core.ui.catalog.formatRupees
import com.sahyog.core.ui.catalog.priceUnitSuffix
import com.sahyog.core.ui.location.formatDistance
import com.sahyog.feature.discovery.R
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun WorkerProfileScreen(
    viewModel: WorkerProfileViewModel,
    onNavigateBack: () -> Unit,
    onBook: (workerId: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkerProfileContent(uiState = uiState, onNavigateBack = onNavigateBack, onBook = onBook)
}

@Composable
internal fun WorkerProfileContent(
    uiState: WorkerProfileUiState,
    onNavigateBack: () -> Unit,
    onBook: (workerId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            SahyogTopAppBar(
                title = (uiState as? WorkerProfileUiState.Ready)?.worker?.name.orEmpty(),
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            val worker = (uiState as? WorkerProfileUiState.Ready)?.worker
            if (worker != null && worker.services.isNotEmpty()) {
                Surface(color = SahyogTheme.colors.surfaceContainer, tonalElevation = SahyogElevation.medium) {
                    SahyogButton(
                        text = stringResource(R.string.discovery_worker_book),
                        onClick = { onBook(worker.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.md),
                    )
                }
            }
        },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            WorkerProfileUiState.Loading -> SahyogLoadingView(modifier)
            WorkerProfileUiState.NotFound -> SahyogEmptyView(
                title = stringResource(R.string.discovery_not_found_title),
                message = stringResource(R.string.discovery_not_found_message),
                modifier = modifier,
            )
            is WorkerProfileUiState.Ready -> WorkerProfile(uiState, modifier)
        }
    }
}

@Composable
private fun WorkerProfile(state: WorkerProfileUiState.Ready, modifier: Modifier) {
    val worker = state.worker
    val distanceKm = state.distanceKm
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
        item(key = "header") { ProfileHeader(worker) }
        item(key = "stats") { StatsRow(worker) }
        if (worker.about.isNotBlank()) {
            item(key = "about") {
                Section(stringResource(R.string.discovery_worker_about)) {
                    Text(worker.about, style = SahyogTheme.typography.bodyLarge)
                }
            }
        }
        if (worker.services.isNotEmpty()) {
            item(key = "services") {
                Section(stringResource(R.string.discovery_worker_services)) {
                    SahyogCard(modifier = Modifier.fillMaxWidth()) {
                        worker.services.forEachIndexed { index, service ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = SahyogSpacing.md),
                                    color = SahyogTheme.colors.outlineVariant,
                                )
                            }
                            ServiceRow(service)
                        }
                    }
                    Spacer(Modifier.height(SahyogSpacing.sm))
                    Text(
                        text = stringResource(R.string.discovery_worker_price_note),
                        style = SahyogTheme.typography.bodySmall,
                        color = SahyogTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "details") {
            Section(stringResource(R.string.discovery_worker_details)) {
                DetailRow(stringResource(R.string.discovery_worker_area), worker.locality)
                if (distanceKm != null) {
                    Spacer(Modifier.height(SahyogSpacing.sm))
                    DetailRow(
                        stringResource(R.string.discovery_worker_distance),
                        stringResource(R.string.discovery_worker_distance_value, formatDistance(distanceKm)),
                    )
                }
                if (worker.languages.isNotEmpty()) {
                    Spacer(Modifier.height(SahyogSpacing.sm))
                    DetailRow(stringResource(R.string.discovery_worker_languages), worker.languages.joinToString(", "))
                }
            }
        }
        item(key = "reviews") { ReviewsSection(state.reviews, state.today) }
    }
}

@Composable
private fun ReviewsSection(reviews: List<WorkerReview>, today: LocalDate) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.discovery_worker_reviews)) {
        if (reviews.isEmpty()) {
            Text(
                text = stringResource(R.string.discovery_worker_reviews_none),
                style = SahyogTheme.typography.bodyMedium,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                val shown = if (showAll) reviews else reviews.take(REVIEWS_PREVIEW_COUNT)
                shown.forEach { ReviewItem(it, today) }
                if (!showAll && reviews.size > REVIEWS_PREVIEW_COUNT) {
                    SahyogTextButton(
                        text = pluralStringResource(R.plurals.discovery_worker_reviews_more, reviews.size, reviews.size),
                        onClick = { showAll = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewItem(review: WorkerReview, today: LocalDate) {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = review.authorName,
                style = SahyogTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatBookingDate(review.createdAt.atZone(ZoneId.systemDefault()).toLocalDate(), today),
                style = SahyogTheme.typography.bodySmall,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(SahyogSpacing.xxs))
        SahyogRatingBar(rating = review.rating.toFloat())
        review.comment?.let {
            Spacer(Modifier.height(SahyogSpacing.xs))
            Text(it, style = SahyogTheme.typography.bodyMedium)
        }
        if (review.serviceName.isNotBlank()) {
            Spacer(Modifier.height(SahyogSpacing.xs))
            Text(
                text = review.serviceName,
                style = SahyogTheme.typography.bodySmall,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileHeader(worker: Worker) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SahyogAvatar(name = worker.name, size = HeaderAvatarSize)
        Spacer(Modifier.width(SahyogSpacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xs)) {
            Text(
                text = worker.name,
                style = SahyogTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = worker.categories.map { categoryLabel(it) }.joinToString(" · "),
                style = SahyogTheme.typography.bodyMedium,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                SahyogStatusBadge(
                    text = stringResource(R.string.discovery_worker_verified),
                    tone = StatusTone.Brand,
                    icon = SahyogIcons.Verified,
                )
                if (!worker.isAvailable) {
                    SahyogStatusBadge(text = stringResource(R.string.discovery_worker_busy), tone = StatusTone.Neutral)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(worker: Worker) {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            StatCell(label = stringResource(R.string.discovery_worker_rating), modifier = Modifier.weight(1f)) {
                WorkerRating(worker)
            }
            StatCell(
                label = stringResource(R.string.discovery_worker_jobs),
                value = worker.jobsCompleted.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(R.string.discovery_worker_experience),
                value = pluralStringResource(
                    R.plurals.discovery_worker_years,
                    worker.yearsExperience,
                    worker.yearsExperience,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.discovery_worker_stat_description, label, value)
    StatCell(label, modifier.clearAndSetSemantics { contentDescription = description }) {
        Text(value, style = SahyogTheme.typography.titleMedium)
    }
}

@Composable
private fun StatCell(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        value()
        Spacer(Modifier.height(SahyogSpacing.xs))
        Text(
            text = label,
            style = SahyogTheme.typography.bodySmall,
            color = SahyogTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ServiceRow(service: WorkerService) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(service.name, style = SahyogTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(SahyogSpacing.md))
        Text(
            text = formatRupees(service.startingPrice) + priceUnitSuffix(service.unit),
            style = SahyogTheme.typography.labelLarge,
            color = SahyogTheme.colors.primary,
        )
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

private val HeaderAvatarSize = 72.dp
private val DetailLabelWidth = 112.dp
private const val REVIEWS_PREVIEW_COUNT = 3

@Preview
@Composable
private fun WorkerProfilePreview() {
    SahyogTheme {
        WorkerProfileContent(
            uiState = WorkerProfileUiState.Ready(
                Worker(
                    id = "1",
                    name = "Ramesh Kumar",
                    categories = listOf(ServiceCategory("electrician", "Electrician")),
                    rating = 4.8f,
                    ratingCount = 212,
                    jobsCompleted = 486,
                    yearsExperience = 12,
                    locality = "Model Town",
                    about = "Licensed electrician handling house wiring and fittings.",
                    languages = listOf("Hindi", "Punjabi"),
                    services = listOf(
                        WorkerService("Fan installation", Paise.rupees(199), PriceUnit.PerVisit),
                        WorkerService("House wiring", Paise.rupees(299), PriceUnit.PerHour),
                    ),
                    isAvailable = true,
                ),
            ),
            onNavigateBack = {},
            onBook = {},
        )
    }
}
