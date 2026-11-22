package com.sahyog.core.ui.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sahyog.core.location.LOCATION_PERMISSIONS
import com.sahyog.core.ui.R
import java.util.Locale
import kotlin.math.roundToInt

/** "800 m", "2.3 km", "12 km". */
@Composable
fun formatDistance(km: Double): String = when {
    km < 1.0 -> stringResource(R.string.core_ui_distance_m, ((km * 1000 / 50).roundToInt() * 50).coerceAtLeast(50))
    km < 10.0 -> stringResource(R.string.core_ui_distance_km, String.format(Locale.ROOT, "%.1f", km))
    else -> stringResource(R.string.core_ui_distance_km, km.roundToInt().toString())
}

enum class LocationPermissionResult {
    Granted,

    /** Refused this time; asking again later is fine. */
    Denied,

    /** "Don't ask again" (or refused twice): only the system settings can grant it now. */
    PermanentlyDenied,
}

/**
 * Returns a function that shows the system location permission dialog. The caller
 * explains *why* first (in its own UI) and calls this only when the user asks for it.
 */
@Composable
fun rememberLocationPermissionRequester(onResult: (LocationPermissionResult) -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        onResult(
            when {
                grants.values.any { it } -> LocationPermissionResult.Granted
                // After a refusal, Android only keeps offering the dialog if rationale is allowed.
                activity != null && LOCATION_PERMISSIONS.any { activity.shouldShowRequestPermissionRationale(it) } ->
                    LocationPermissionResult.Denied
                else -> LocationPermissionResult.PermanentlyDenied
            },
        )
    }
    return { launcher.launch(LOCATION_PERMISSIONS) }
}

/** Opens this app's page in system settings, where the user can allow location. */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
