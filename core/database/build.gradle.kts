plugins {
    id("sahyog.android.library")
    id("sahyog.hilt")
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.sahyog.core.database"
}

room {
    // Checked into git: each schema version is the baseline for writing and testing migrations.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    // `api`: Room types (e.g. @Dao interfaces) are part of this module's public surface.
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
