plugins {
    id("sahyog.android.feature")
}

android {
    namespace = "com.sahyog.feature.admin"
}

dependencies {
    // LocalActivity: blocks screenshots while an identity document is on screen.
    implementation(libs.androidx.activity.compose)
}
