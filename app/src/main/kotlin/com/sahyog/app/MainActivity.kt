package com.sahyog.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sahyog.core.designsystem.catalog.DesignSystemCatalog
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity. All screens are composables hosted here; navigation between
 * them is handled by Navigation Compose (added in Milestone 3).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            // Temporary in-memory toggle. Becomes the persisted Light/Dark/System preference in M3.
            var darkTheme by rememberSaveable { mutableStateOf(systemDark) }

            // The status/navigation bar icon color must follow the APP's theme, not the system's.
            // Otherwise dark icons sit on a dark app background when the two disagree.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                )
                onDispose {}
            }

            // Temporary: shows the design-system catalog until the navigation shell lands.
            DesignSystemCatalog(darkTheme = darkTheme, onDarkThemeChange = { darkTheme = it })
        }
    }
}

// Same scrims androidx.activity uses by default for 3-button navigation bars.
private val LightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
