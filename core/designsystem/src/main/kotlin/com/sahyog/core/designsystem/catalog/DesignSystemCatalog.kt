package com.sahyog.core.designsystem.catalog

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogErrorView
import com.sahyog.core.designsystem.component.SahyogFilterChip
import com.sahyog.core.designsystem.component.SahyogLogo
import com.sahyog.core.designsystem.component.SahyogLogoMark
import com.sahyog.core.designsystem.component.SahyogNavigationBar
import com.sahyog.core.designsystem.component.SahyogNavigationBarItem
import com.sahyog.core.designsystem.component.SahyogOutlinedButton
import com.sahyog.core.designsystem.component.SahyogRatingBar
import com.sahyog.core.designsystem.component.SahyogRatingSummary
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogBrand
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme

/*
 * Developer-facing showcase of every token and component, in light and dark.
 * Demo copy is hardcoded on purpose: this screen is never shown to end users.
 * It moves to a debug-only entry point once the navigation shell exists (M3).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSystemCatalog(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    SahyogTheme(darkTheme = darkTheme) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = SahyogTheme.colors.surface,
            topBar = {
                SahyogTopAppBar(
                    title = "Design system",
                    scrollBehavior = scrollBehavior,
                    actions = {
                        Text("Dark", style = SahyogTheme.typography.labelLarge)
                        Spacer(Modifier.width(SahyogSpacing.sm))
                        Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
                        Spacer(Modifier.width(SahyogSpacing.lg))
                    },
                )
            },
            bottomBar = {
                SahyogNavigationBar {
                    CatalogTabs.forEachIndexed { index, tab ->
                        SahyogNavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = tab.icon,
                            selectedIcon = tab.selectedIcon,
                            label = tab.label,
                        )
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xxxl),
            ) {
                item { Section("Brand") { BrandSection() } }
                item { Section("Color") { ColorSection() } }
                item { Section("Typography") { TypographySection() } }
                item { Section("Buttons") { ButtonSection() } }
                item { Section("Inputs") { InputSection() } }
                item { Section("Chips & badges") { ChipSection() } }
                item { Section("Cards") { WorkerCardSample() } }
                item { Section("States") { StateSection() } }
                item { Spacer(Modifier.height(SahyogSpacing.xxl)) }
            }
        }
    }
}

private data class CatalogTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val CatalogTabs = listOf(
    CatalogTab("Home", SahyogIcons.HomeOutlined, SahyogIcons.Home),
    CatalogTab("Search", SahyogIcons.SearchOutlined, SahyogIcons.Search),
    CatalogTab("Bookings", SahyogIcons.BookingsOutlined, SahyogIcons.Bookings),
    CatalogTab("Account", SahyogIcons.AccountOutlined, SahyogIcons.Account),
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = SahyogSpacing.screenGutter)) {
        Text(
            text = title.uppercase(),
            style = SahyogTheme.typography.labelMedium,
            color = SahyogTheme.colors.primary,
        )
        Spacer(Modifier.height(SahyogSpacing.md))
        content()
    }
}

@Composable
private fun BrandSection() {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        SahyogLogo(markHeight = 40.dp)
        Spacer(Modifier.height(SahyogSpacing.sm))
        Text(
            "Connecting Skills. Empowering Communities.",
            style = SahyogTheme.typography.bodyMedium,
            color = SahyogTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(SahyogSpacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.lg)) {
            // Launcher-icon preview: white + marigold rings on brand teal.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(SahyogBrand.Teal, SahyogTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                SahyogLogoMark(height = 26.dp, primaryRing = Color.White)
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(SahyogBrand.Teal, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SahyogLogoMark(height = 26.dp, primaryRing = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSection() {
    val c = SahyogTheme.colors
    val x = SahyogTheme.extendedColors
    val swatches = listOf(
        Triple("Primary", c.primary, c.onPrimary),
        Triple("Primary container", c.primaryContainer, c.onPrimaryContainer),
        Triple("Secondary", c.secondary, c.onSecondary),
        Triple("Secondary container", c.secondaryContainer, c.onSecondaryContainer),
        Triple("Tertiary", c.tertiary, c.onTertiary),
        Triple("Error", c.error, c.onError),
        Triple("Success", x.success, x.onSuccess),
        Triple("Warning", x.warning, x.onWarning),
        Triple("Surface", c.surface, c.onSurface),
        Triple("Surface container", c.surfaceContainer, c.onSurface),
        Triple("Card", x.card, c.onSurface),
        Triple("Rating", x.rating, c.surface),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm),
        maxItemsInEachRow = 2,
    ) {
        swatches.forEach { (name, bg, fg) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .background(bg, SahyogTheme.shapes.medium)
                    .border(1.dp, c.outlineVariant.copy(alpha = 0.5f), SahyogTheme.shapes.medium)
                    .padding(SahyogSpacing.md),
            ) {
                Text(name, color = fg, style = SahyogTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun TypographySection() {
    val t = SahyogTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
        Text("Display small", style = t.displaySmall)
        Text("Headline medium", style = t.headlineMedium)
        Text("Title large", style = t.titleLarge)
        Text("Title medium", style = t.titleMedium)
        Text("Body large: trusted local professionals near you.", style = t.bodyLarge)
        Text("Body medium: book in under a minute.", style = t.bodyMedium)
        Text("हिंदी: आपके पास भरोसेमंद कारीगर।", style = t.bodyLarge)
        Text("LABEL LARGE", style = t.labelLarge)
    }
}

@Composable
private fun ButtonSection() {
    var loading by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md)) {
        SahyogButton(
            text = if (loading) "Booking" else "Book now (tap to toggle loading)",
            onClick = { loading = !loading },
            modifier = Modifier.fillMaxWidth(),
        )
        SahyogButton(
            text = "Stop loading",
            onClick = { loading = false },
            loading = loading,
            modifier = Modifier.fillMaxWidth(),
        )
        SahyogOutlinedButton(
            text = "Call worker",
            onClick = {},
            leadingIcon = SahyogIcons.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
            SahyogButton(text = "Disabled", onClick = {}, enabled = false)
            SahyogTextButton(text = "Resend code", onClick = {})
        }
    }
}

@Composable
private fun InputSection() {
    var phone by rememberSaveable { mutableStateOf("98765") }
    var name by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md)) {
        SahyogTextField(
            value = phone,
            onValueChange = { phone = it.filter(Char::isDigit).take(10) },
            label = "Mobile number",
            prefix = "+91 ",
            leadingIcon = SahyogIcons.Phone,
            errorText = if (phone.length in 1..9) "Enter a 10-digit mobile number" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        SahyogTextField(
            value = name,
            onValueChange = { name = it },
            label = "Full name",
            placeholder = "As on your ID",
            supportingText = "Shown to customers on your profile",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChipSection() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val categories = listOf("All", "Electrician", "Plumber", "Carpenter", "Painter", "Cleaning")
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm),
        ) {
            categories.forEachIndexed { index, label ->
                SahyogFilterChip(label = label, selected = selected == index, onClick = { selected = index })
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm),
        ) {
            SahyogStatusBadge("Verified", StatusTone.Success, icon = SahyogIcons.Verified)
            SahyogStatusBadge("Requested", StatusTone.Info)
            SahyogStatusBadge("Pending payment", StatusTone.Warning)
            SahyogStatusBadge("Cancelled", StatusTone.Error)
            SahyogStatusBadge("Co-op member", StatusTone.Brand)
            SahyogStatusBadge("Draft", StatusTone.Neutral)
        }
    }
}

@Composable
private fun WorkerCardSample() {
    SahyogCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SahyogAvatar(name = "Ramesh Kumar", size = 52.dp)
            Spacer(Modifier.width(SahyogSpacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ramesh Kumar", style = SahyogTheme.typography.titleMedium)
                    Spacer(Modifier.width(SahyogSpacing.xs))
                    Icon(
                        SahyogIcons.Verified,
                        contentDescription = "Verified",
                        tint = SahyogTheme.colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    "Electrician · 8 yrs experience",
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = {}) {
                Icon(SahyogIcons.Forward, contentDescription = "View profile")
            }
        }
        Spacer(Modifier.height(SahyogSpacing.md))
        HorizontalDivider(color = SahyogTheme.colors.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(SahyogSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SahyogRatingSummary(rating = 4.8f, reviewCount = 126)
            Spacer(Modifier.width(SahyogSpacing.lg))
            Icon(
                SahyogIcons.Location,
                contentDescription = null,
                tint = SahyogTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(SahyogSpacing.xxs))
            Text("1.2 km", style = SahyogTheme.typography.bodySmall, color = SahyogTheme.colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("₹299", style = SahyogTheme.typography.titleMedium, color = SahyogTheme.colors.primary)
            Text(" /visit", style = SahyogTheme.typography.bodySmall, color = SahyogTheme.colors.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(SahyogSpacing.md))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SahyogRatingBar(rating = 3.5f, starSize = 20.dp)
        Spacer(Modifier.width(SahyogSpacing.sm))
        Text("3.5 fractional rating", style = SahyogTheme.typography.bodySmall)
    }
}

@Composable
private fun StateSection() {
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md)) {
        SahyogCard(modifier = Modifier.fillMaxWidth().height(300.dp), contentPadding = PaddingValues(0.dp)) {
            SahyogEmptyView(
                title = "No bookings yet",
                message = "Services you book will appear here.",
                icon = SahyogIcons.Bookings,
                actionLabel = "Explore services",
                onAction = {},
            )
        }
        SahyogCard(modifier = Modifier.fillMaxWidth().height(300.dp), contentPadding = PaddingValues(0.dp)) {
            SahyogErrorView(
                title = "Couldn't load workers",
                message = "Check your internet connection and try again.",
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Light", showBackground = true, heightDp = 2400)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, heightDp = 2400)
@Composable
private fun DesignSystemCatalogPreview() {
    DesignSystemCatalog(darkTheme = isSystemInDarkTheme(), onDarkThemeChange = {})
}
