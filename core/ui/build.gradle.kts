plugins {
    id("sahyog.android.library")
    id("sahyog.android.compose")
}

android {
    namespace = "com.sahyog.core.ui"
}

dependencies {
    api(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:location"))
    // Permission request launcher (rememberLauncherForActivityResult, LocalActivity).
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}
