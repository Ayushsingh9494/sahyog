package com.sahyog.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Process entry point. @HiltAndroidApp generates the root (singleton) DI container;
 * every @Inject in the app ultimately resolves from here.
 */
@HiltAndroidApp
class SahyogApplication : Application()
