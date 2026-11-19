plugins {
    id("sahyog.android.library")
}

android {
    namespace = "com.sahyog.core.testing"
}

dependencies {
    api(project(":core:data"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
