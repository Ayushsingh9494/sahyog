package com.sahyog.feature.discovery.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.ui.catalog.categoryIconRes
import com.sahyog.core.ui.catalog.categoryLabel

/** Icon-over-label category button used in the Home grid. */
@Composable
internal fun CategoryTile(
    category: ServiceCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(SahyogTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = SahyogSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(TileIconContainer)
                .background(SahyogTheme.colors.primaryContainer, SahyogTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(categoryIconRes(category.id)),
                contentDescription = null,
                tint = SahyogTheme.colors.onPrimaryContainer,
                modifier = Modifier.size(TileIcon),
            )
        }
        Spacer(Modifier.height(SahyogSpacing.sm))
        Text(
            text = categoryLabel(category),
            style = SahyogTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A fixed-column grid for use inside a LazyColumn item (the category list is short). */
@Composable
internal fun CategoryGrid(
    categories: List<ServiceCategory>,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xs)) {
        categories.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.xs)) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        onClick = { onCategoryClick(category.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep tiles the same width on a partially filled last row.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SahyogTheme.typography.titleMedium,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
}

private val TileIconContainer = 56.dp
private val TileIcon = 28.dp
