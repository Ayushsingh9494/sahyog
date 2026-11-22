package com.sahyog.core.data.auth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the currently resumed Activity, held weakly so it can never leak.
 *
 * Why: when Play Integrity can't verify the device (e.g. no Google Play services),
 * Firebase falls back to a reCAPTCHA check, which needs an Activity to show it.
 * The repository has no Activity of its own, so it borrows the foreground one.
 * Registered once in the Application class.
 */
@Singleton
class ForegroundActivityTracker @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var current: WeakReference<Activity>? = null

    val activity: Activity? get() = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
