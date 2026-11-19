package com.sahyog.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.app.ui.SahyogApp
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.ThemePreference
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity. Shows the splash until the session is known, then hands
 * everything to [SahyogApp], which decides which section of the app to render.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the splash theme is swapped correctly.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep the splash up until we know whether to show login, onboarding or home.
        // Avoids flashing the wrong screen on launch.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainActivityUiState.Loading }

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = uiState as? MainActivityUiState.Ready
            val darkTheme = shouldUseDarkTheme(ready?.theme ?: ThemePreference.System)

            // Status/navigation bar icons must follow the APP's theme, not the system's.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                )
                onDispose {}
            }

            SahyogTheme(darkTheme = darkTheme) {
                if (ready != null) {
                    SahyogApp(
                        session = ready.session,
                        darkTheme = darkTheme,
                        onThemeChange = viewModel::setTheme,
                        showDeveloperOptions = BuildConfig.DEBUG,
                    )
                }
            }
        }
    }
}

@Composable
private fun shouldUseDarkTheme(theme: ThemePreference): Boolean = when (theme) {
    ThemePreference.System -> isSystemInDarkTheme()
    ThemePreference.Light -> false
    ThemePreference.Dark -> true
}

// Same scrims androidx.activity uses by default for 3-button navigation bars.
private val LightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
