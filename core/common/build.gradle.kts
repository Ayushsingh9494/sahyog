plugins {
    id("sahyog.jvm.library")
    id("sahyog.hilt")
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
