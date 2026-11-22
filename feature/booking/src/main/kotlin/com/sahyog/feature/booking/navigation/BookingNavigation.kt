package com.sahyog.feature.booking.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sahyog.feature.booking.book.BookServiceScreen
import com.sahyog.feature.booking.book.BookServiceViewModel
import com.sahyog.feature.booking.detail.BookingDetailScreen
import com.sahyog.feature.booking.detail.BookingDetailViewModel
import com.sahyog.feature.booking.list.BookingsScreen
import kotlinx.serialization.Serializable

/** The Bookings bottom-nav tab. */
@Serializable data object BookingsRoute

@Serializable internal data class BookServiceRoute(val workerId: String)

@Serializable internal data class BookingDetailRoute(val bookingId: String)

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
        BookServiceScreen(
            viewModel = hiltViewModel<BookServiceViewModel, BookServiceViewModel.Factory>(
                creationCallback = { factory -> factory.create(route.workerId) },
            ),
            onNavigateBack = navController::navigateUp,
            onBooked = { bookingId ->
                // Replace the form with the new booking, so Back doesn't reopen a submitted form.
                navController.navigate(BookingDetailRoute(bookingId)) {
                    popUpTo<BookServiceRoute> { inclusive = true }
                }
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
