package com.sahyog.app

import android.app.Application
import com.sahyog.core.data.auth.ForegroundActivityTracker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Process entry point. @HiltAndroidApp generates the root (singleton) DI container;
 * every @Inject in the app ultimately resolves from here.
 */
@HiltAndroidApp
class SahyogApplication : Application() {

    @Inject lateinit var foregroundActivityTracker: ForegroundActivityTracker

    override fun onCreate() {
        super.onCreate() // Hilt injects the fields above during this call.
        // Lets phone auth show its reCAPTCHA fallback on devices without Play Integrity.
        registerActivityLifecycleCallbacks(foregroundActivityTracker)
    }
}
