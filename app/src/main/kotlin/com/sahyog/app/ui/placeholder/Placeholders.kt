package com.sahyog.app.ui.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.sahyog.app.R
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme

/*
 * Temporary tab content so the navigation shell can be used end to end.
 * Each one is replaced by a real feature module (worker Schedule and Earnings: M7b).
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
