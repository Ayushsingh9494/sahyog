plugins {
    id("sahyog.android.library")
    id("sahyog.hilt")
}

android {
    namespace = "com.sahyog.core.location"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.play.services.location)
    // Task.await() for the Play services location calls.
    implementation(libs.kotlinx.coroutines.play.services)
}
