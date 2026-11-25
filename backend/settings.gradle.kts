// The Sahyog API server. A separate Gradle build from the Android app on purpose: the app's
// toolchain (AGP's bundled Kotlin) and the server's (Spring Boot's Kotlin) move independently.
rootProject.name = "sahyog-server"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
