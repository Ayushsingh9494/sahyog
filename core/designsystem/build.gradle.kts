plugins {
    id("sahyog.android.library")
    id("sahyog.android.compose")
}

android {
    namespace = "com.sahyog.core.designsystem"
}

dependencies {
    // Exposed as `api`: every screen that uses SahyogTheme also needs Material 3 types.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    // Implementation only: screens reach icons through SahyogIcons, never directly.
    implementation(libs.androidx.compose.material.icons.core)

    testImplementation(libs.junit)
}
