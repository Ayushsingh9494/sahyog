plugins {
    id("sahyog.android.library")
    id("sahyog.hilt")
}

android {
    namespace = "com.sahyog.core.data"

    testOptions {
        // Firebase exception constructors call android.text.TextUtils; in JVM unit tests
        // that stub would otherwise throw. Defaults are enough for error-mapping tests.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Repository signatures use these types, so consumers get them transitively.
    api(project(":core:common"))
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // Task.await(): turns Firebase's callback-based Tasks into suspend calls.
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.datastore.preferences)
}
