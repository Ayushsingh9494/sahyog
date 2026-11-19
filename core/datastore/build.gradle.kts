plugins {
    id("sahyog.android.library")
    id("sahyog.hilt")
}

android {
    namespace = "com.sahyog.core.datastore"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
}
