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
    implementation(libs.androidx.lifecycle.runtime.compose)
}
