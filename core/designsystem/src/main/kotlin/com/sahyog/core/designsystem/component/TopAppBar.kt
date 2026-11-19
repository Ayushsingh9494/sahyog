package com.sahyog.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.sahyog.core.designsystem.R
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogTheme

/** Screen top bar. Shows a back arrow when [onNavigateBack] is provided. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SahyogTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    SahyogTopAppBar(
        title = title,
        scrollBehavior = null,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        actions = actions,
    )
}

/**
 * Variant that tints as content scrolls under it. Material's scroll-behavior API is
 * still experimental, so only callers that need it have to opt in.
 */
@ExperimentalMaterial3Api
@Composable
fun SahyogTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior?,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = SahyogTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(SahyogIcons.Back, contentDescription = stringResource(R.string.designsystem_navigate_back))
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SahyogTheme.colors.surface,
            scrolledContainerColor = SahyogTheme.colors.surfaceContainer,
        ),
    )
}
