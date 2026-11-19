package com.sahyog.feature.auth.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sahyog.feature.auth.otp.OtpScreen
import com.sahyog.feature.auth.otp.OtpViewModel
import com.sahyog.feature.auth.phone.PhoneEntryScreen
import com.sahyog.feature.auth.welcome.WelcomeScreen
import kotlinx.serialization.Serializable

/*
 * Type-safe routes: each destination is a @Serializable type, and its arguments
 * are constructor properties. The compiler checks every navigate() call.
 */
@Serializable internal data object WelcomeRoute

@Serializable internal data object PhoneEntryRoute

@Serializable internal data class OtpRoute(val phoneNumber: String)

/**
 * The signed-out section of the app. It has no "success" callback: when OTP
 * verification succeeds, the session changes and the app root replaces this
 * whole section, discarding its back stack.
 */
@Composable
fun AuthNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = WelcomeRoute, modifier = modifier) {
        composable<WelcomeRoute> {
            WelcomeScreen(onGetStarted = { navController.navigate(PhoneEntryRoute) })
        }
        composable<PhoneEntryRoute> {
            PhoneEntryScreen(
                onNavigateBack = navController::navigateUp,
                onOtpSent = { phone -> navController.navigate(OtpRoute(phone)) },
            )
        }
        composable<OtpRoute> { entry ->
            val route = entry.toRoute<OtpRoute>()
            OtpScreen(
                onNavigateBack = navController::navigateUp,
                // Assisted injection: the phone number is passed straight into the ViewModel's
                // constructor, which keeps the ViewModel trivially unit-testable.
                viewModel = hiltViewModel<OtpViewModel, OtpViewModel.Factory>(
                    creationCallback = { factory -> factory.create(route.phoneNumber) },
                ),
            )
        }
    }
}
