import java.util.Properties

plugins {
    id("sahyog.android.library")
    id("sahyog.hilt")
    // JSON bodies of the Sahyog API.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sahyog.core.data"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Where the Sahyog API is. Locally the server runs on the laptop and the phone reaches it
        // over USB (`adb reverse tcp:8080 tcp:8080`), so 127.0.0.1 is right. Override with
        // SAHYOG_API_URL in local.properties (e.g. the Cloud Run https URL).
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"")
    }

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
    // `api`: features use DeviceLocationResult and LOCATION_PERMISSIONS through the repository.
    api(project(":core:location"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // Task.await(): turns Firebase's callback-based Tasks into suspend calls.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    // Retrofit 3 brings OkHttp 4; pin the current 5.x line.
    implementation(libs.okhttp)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.datastore.preferences)
    // A real local HTTP server for testing the API client (headers, errors, bodies).
    testImplementation(libs.okhttp.mockwebserver)
}

/** SAHYOG_API_URL from local.properties, else the laptop server over `adb reverse`. Ends with "/". */
fun apiBaseUrl(): String {
    val file = rootProject.file("local.properties")
    val configured = if (file.exists()) {
        Properties().apply { file.inputStream().use(::load) }.getProperty("SAHYOG_API_URL")
    } else {
        null
    }
    return (configured?.trim()?.ifEmpty { null } ?: "http://127.0.0.1:8080/").let { if (it.endsWith("/")) it else "$it/" }
}
