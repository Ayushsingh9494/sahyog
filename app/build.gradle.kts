plugins {
    id("sahyog.android.application")
    id("sahyog.android.compose")
    id("sahyog.hilt")
}

android {
    namespace = "com.sahyog.app"

    defaultConfig {
        // Permanent once uploaded to Play. Change it before the first release if needed.
        applicationId = "com.sahyog.app"
        versionCode = 1
        versionName = "0.1.0"
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
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
