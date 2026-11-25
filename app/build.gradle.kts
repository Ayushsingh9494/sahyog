import java.util.Properties

plugins {
    id("sahyog.android.application")
    id("sahyog.android.compose")
    id("sahyog.hilt")
    alias(libs.plugins.kotlin.serialization)
    // Reads app/google-services.json (Firebase project config) at build time.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.sahyog.app"

    defaultConfig {
        // Permanent once uploaded to Play. Change it before the first release if needed.
        applicationId = "com.sahyog.app"
        versionCode = 1
        versionName = "0.1.0"

        // Google Maps key: read from the untracked local.properties (or a CI secret), never
        // committed. Without one the app still builds; it just hides the map picker.
        manifestPlaceholders["mapsApiKey"] = mapsApiKey()
    }

    buildFeatures {
        // BuildConfig.DEBUG gates developer-only screens (design catalog).
        buildConfig = true
    }

    buildTypes {
        release {
            // R8 shrinking/obfuscation gets enabled and verified in the hardening milestone.
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":feature:account"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:booking"))
    implementation(project(":feature:discovery"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:worker"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/** `MAPS_API_KEY` from local.properties, else the environment (CI), else empty. */
fun mapsApiKey(): String {
    val localFile = rootProject.file("local.properties")
    val local = if (localFile.exists()) {
        Properties().apply { localFile.inputStream().use(::load) }.getProperty("MAPS_API_KEY")
    } else {
        null
    }
    return (local ?: System.getenv("MAPS_API_KEY")).orEmpty().trim()
}
