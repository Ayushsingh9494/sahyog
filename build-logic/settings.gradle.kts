dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    // Share the main project's version catalog so versions are defined in exactly one place.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
