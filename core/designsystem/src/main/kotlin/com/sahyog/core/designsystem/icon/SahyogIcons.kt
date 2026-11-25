package com.sahyog.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The only icon entry point for the app. Screens never import material icons
 * directly, so the icon set can be swapped (e.g. to Material Symbols) in one file.
 * Rounded style matches the brand's soft corners.
 */
object SahyogIcons {
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val Forward: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward
    val Close: ImageVector = Icons.Rounded.Close

    val Home: ImageVector = Icons.Rounded.Home
    val HomeOutlined: ImageVector = Icons.Outlined.Home
    val Search: ImageVector = Icons.Rounded.Search
    val SearchOutlined: ImageVector = Icons.Outlined.Search
    val Bookings: ImageVector = Icons.Rounded.DateRange
    val BookingsOutlined: ImageVector = Icons.Outlined.DateRange
    val Account: ImageVector = Icons.Rounded.Person
    val AccountOutlined: ImageVector = Icons.Outlined.Person
    val Jobs: ImageVector = Icons.AutoMirrored.Rounded.List

    val Service: ImageVector = Icons.Rounded.Build
    val Location: ImageVector = Icons.Rounded.LocationOn
    val Phone: ImageVector = Icons.Rounded.Call
    val Notifications: ImageVector = Icons.Rounded.Notifications
    val Settings: ImageVector = Icons.Rounded.Settings
    val Star: ImageVector = Icons.Rounded.Star

    val Verified: ImageVector = Icons.Rounded.CheckCircle
    val VerifiedOutlined: ImageVector = Icons.Outlined.CheckCircle
    val Check: ImageVector = Icons.Rounded.Check
    /** Admin / restricted areas. */
    val Lock: ImageVector = Icons.Rounded.Lock
    val Info: ImageVector = Icons.Rounded.Info
    val Warning: ImageVector = Icons.Rounded.Warning
    val Retry: ImageVector = Icons.Rounded.Refresh
}
