package com.sahyog.feature.booking.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sahyog.feature.booking.book.BookServiceScreen
import com.sahyog.feature.booking.book.BookServiceViewModel
import com.sahyog.feature.booking.detail.BookingDetailScreen
import com.sahyog.feature.booking.detail.BookingDetailViewModel
import com.sahyog.feature.booking.list.BookingsScreen
import com.sahyog.feature.booking.map.MapPickerScreen
import com.sahyog.feature.booking.map.MapPickerViewModel
import com.sahyog.core.model.GeoPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The Bookings bottom-nav tab. */
@Serializable data object BookingsRoute

@Serializable internal data class BookServiceRoute(val workerId: String)

@Serializable internal data class BookingDetailRoute(val bookingId: String)

/** @property latitude With [longitude], a pin chosen earlier, to adjust it. */
@Serializable internal data class MapPickerRoute(val latitude: Double? = null, val longitude: Double? = null)

/** What the map picker hands back to the booking form (via its saved state). */
@Serializable private data class PickedLocation(val latitude: Double, val longitude: Double, val address: String?)

private const val PICKED_LOCATION_KEY = "picked_location"

fun NavController.navigateToBookService(workerId: String) = navigate(BookServiceRoute(workerId))

/**
 * Registers the booking destinations.
 *
 * @param onWorkerClick Opens a worker's profile (owned by feature:discovery, wired in :app).
 * @param onFindService Takes the customer somewhere to start a booking (the Home tab).
 */
fun NavGraphBuilder.bookingDestinations(
    navController: NavController,
    onWorkerClick: (String) -> Unit,
    onFindService: () -> Unit,
) {
    composable<BookingsRoute> {
        BookingsScreen(
            onBookingClick = { id -> navController.navigate(BookingDetailRoute(id)) },
            onFindService = onFindService,
        )
    }
    composable<BookServiceRoute> { entry ->
        val route = entry.toRoute<BookServiceRoute>()
        val viewModel = hiltViewModel<BookServiceViewModel, BookServiceViewModel.Factory>(
            creationCallback = { factory -> factory.create(route.workerId) },
        )
        // A pin coming back from the map picker: apply it once, then forget it.
        val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_LOCATION_KEY, null)
            .collectAsStateWithLifecycle()
        LaunchedEffect(picked) {
            val json = picked ?: return@LaunchedEffect
            val location = Json.decodeFromString<PickedLocation>(json)
            viewModel.onLocationPicked(GeoPoint(location.latitude, location.longitude), location.address)
            entry.savedStateHandle[PICKED_LOCATION_KEY] = null
        }
        BookServiceScreen(
            viewModel = viewModel,
            onNavigateBack = navController::navigateUp,
            onPickOnMap = { current -> navController.navigate(MapPickerRoute(current?.latitude, current?.longitude)) },
            onBooked = { bookingId ->
                // Replace the form with the new booking, so Back doesn't reopen a submitted form.
                navController.navigate(BookingDetailRoute(bookingId)) {
                    popUpTo<BookServiceRoute> { inclusive = true }
                }
            },
        )
    }
    composable<MapPickerRoute> { entry ->
        val route = entry.toRoute<MapPickerRoute>()
        MapPickerScreen(
            viewModel = hiltViewModel<MapPickerViewModel, MapPickerViewModel.Factory>(
                creationCallback = { factory -> factory.create(GeoPoint.ofOrNull(route.latitude, route.longitude)) },
            ),
            onNavigateBack = navController::navigateUp,
            onPicked = { point, address ->
                navController.previousBackStackEntry?.savedStateHandle?.set(
                    PICKED_LOCATION_KEY,
                    Json.encodeToString(PickedLocation(point.latitude, point.longitude, address)),
                )
                navController.navigateUp()
            },
        )
    }
    composable<BookingDetailRoute> { entry ->
        val route = entry.toRoute<BookingDetailRoute>()
        BookingDetailScreen(
            viewModel = hiltViewModel<BookingDetailViewModel, BookingDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(route.bookingId) },
            ),
            onNavigateBack = navController::navigateUp,
            onWorkerClick = onWorkerClick,
        )
    }
}
