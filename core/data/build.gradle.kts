plugins {
    id("sahyog.android.library")
    id("sahyog.hilt")
}

android {
    namespace = "com.sahyog.core.data"
}

dependencies {
    // Repository signatures use these types, so consumers get them transitively.
    api(project(":core:common"))
    api(project(":core:model"))
    implementation(project(":core:datastore"))
}
