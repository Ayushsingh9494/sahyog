plugins {
    id("sahyog.android.feature")
}

android {
    namespace = "com.sahyog.feature.worker"
}

dependencies {
    // Photo Picker (ActivityResultContracts.PickVisualMedia) for the ID photo. No storage permission needed.
    implementation(libs.androidx.activity.compose)
}
