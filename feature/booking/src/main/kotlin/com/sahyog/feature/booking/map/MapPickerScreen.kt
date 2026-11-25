package com.sahyog.feature.booking.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogElevation
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.core.ui.location.openAppSettings
import com.sahyog.core.ui.location.rememberLocationPermissionRequester
import com.sahyog.feature.booking.R
import kotlinx.coroutines.launch

/** Whether this build has a Maps key; without one the picker is never offered. */
internal fun Context.hasMapsKey(): Boolean = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
    } else {
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    }
    !info.metaData?.getString(MAPS_KEY_META_DATA).isNullOrBlank()
}.getOrDefault(false)

private const val MAPS_KEY_META_DATA = "com.google.android.geo.API_KEY"

@Composable
fun MapPickerScreen(
    viewModel: MapPickerViewModel,
    onNavigateBack: () -> Unit,
    onPicked: (GeoPoint, String?) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.booking_map_title), onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        val start = uiState.start
        if (start == null) {
            SahyogLoadingView(modifier)
        } else {
            MapPicker(viewModel, uiState, start, snackbarHostState, onPicked, modifier)
        }
    }
}

@Composable
private fun MapPicker(
    viewModel: MapPickerViewModel,
    uiState: MapPickerUiState,
    start: MapStart,
    snackbarHostState: SnackbarHostState,
    onPicked: (GeoPoint, String?) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val requestPermission = rememberLocationPermissionRequester(viewModel::onPermissionResult)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start.point.toLatLng(), start.zoom)
    }

    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            is MapPickerEvent.MoveTo -> scope.launch {
                cameraState.animate(
                    CameraUpdateFactory.newLatLngZoom(event.point.toLatLng(), MapPickerViewModel.PIN_ZOOM),
                    MOVE_DURATION_MS,
                )
            }
            MapPickerEvent.RequestPermission -> requestPermission()
            is MapPickerEvent.Problem -> scope.launch {
                val forGood = event.kind == LocationProblem.PermissionDeniedForGood
                val result = snackbarHostState.showSnackbar(
                    message = resources.getString(event.kind.messageRes()),
                    actionLabel = if (forGood) resources.getString(R.string.booking_map_open_settings) else null,
                )
                if (forGood && result == SnackbarResult.ActionPerformed) context.openAppSettings()
            }
            is MapPickerEvent.Picked -> onPicked(event.point, event.address)
        }
    }

    // Each time the customer stops moving the map, look up what's under the pin.
    LaunchedEffect(cameraState.isMoving) {
        if (!cameraState.isMoving) viewModel.onCameraIdle(cameraState.position.target.toGeoPoint())
    }

    Column(modifier) {
        Box(Modifier.weight(1f)) {
            PinMap(cameraState, uiState.hasPermission)
            CentrePin(Modifier.align(Alignment.Center))
            Text(
                text = stringResource(R.string.booking_map_hint),
                style = SahyogTheme.typography.labelLarge,
                color = SahyogTheme.colors.onSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = SahyogSpacing.md)
                    .background(SahyogTheme.colors.surfaceContainerHighest, SahyogTheme.shapes.large)
                    .padding(horizontal = SahyogSpacing.md, vertical = SahyogSpacing.sm),
            )
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.booking_map_my_location)) },
                icon = { Icon(SahyogIcons.Location, contentDescription = null) },
                onClick = viewModel::onMyLocation,
                expanded = !uiState.isLocating,
                containerColor = SahyogTheme.colors.surfaceContainerHighest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(SahyogSpacing.md),
            )
        }
        AddressPanel(uiState, viewModel::onConfirm)
    }
}

// Shown only when the permission is held (hasPermission), which the blue dot needs.
@SuppressLint("MissingPermission")
@Composable
private fun PinMap(cameraState: CameraPositionState, hasPermission: Boolean) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = MapProperties(isMyLocationEnabled = hasPermission),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
        ),
    )
}

/** The pin's tip marks the map centre: the icon is lifted by half its height. */
@Composable
private fun CentrePin(modifier: Modifier) {
    Icon(
        imageVector = SahyogIcons.Location,
        contentDescription = null,
        tint = SahyogTheme.colors.primary,
        modifier = modifier
            .offset(y = -PinSize / 2)
            .size(PinSize),
    )
}

@Composable
private fun AddressPanel(state: MapPickerUiState, onConfirm: () -> Unit) {
    Surface(color = SahyogTheme.colors.surfaceContainer, tonalElevation = SahyogElevation.medium) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.booking_map_label),
                style = SahyogTheme.typography.labelLarge,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(SahyogSpacing.xs))
            Text(
                text = when {
                    state.isLookingUp -> stringResource(R.string.booking_map_finding)
                    state.address != null -> state.address
                    else -> stringResource(R.string.booking_map_no_address)
                },
                style = SahyogTheme.typography.bodyLarge,
                color = if (state.address != null && !state.isLookingUp) {
                    SahyogTheme.colors.onSurface
                } else {
                    SahyogTheme.colors.onSurfaceVariant
                },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(SahyogSpacing.md))
            SahyogButton(
                text = stringResource(R.string.booking_map_confirm),
                onClick = onConfirm,
                enabled = state.canConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun LocationProblem.messageRes(): Int = when (this) {
    LocationProblem.PermissionDenied -> R.string.booking_map_permission_denied
    LocationProblem.PermissionDeniedForGood -> R.string.booking_map_permission_denied
    LocationProblem.LocationOff -> R.string.booking_map_location_off
    LocationProblem.Unavailable -> R.string.booking_map_location_unavailable
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

private fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)

private val PinSize = 48.dp
private const val MOVE_DURATION_MS = 600
