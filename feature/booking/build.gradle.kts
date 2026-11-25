plugins {
    id("sahyog.android.feature")
}

android {
    namespace = "com.sahyog.feature.booking"
}

dependencies {
    // The map pin address picker.
    implementation(libs.maps.compose)
}
