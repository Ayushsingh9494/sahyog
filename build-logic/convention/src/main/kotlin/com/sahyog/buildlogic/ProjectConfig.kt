package com.sahyog.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Single source of truth for SDK levels across every Android module. */
internal object SahyogSdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 26
}

internal val JAVA_VERSION = JavaVersion.VERSION_17
private val KOTLIN_JVM_TARGET = JvmTarget.JVM_17

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** Shared Android configuration for both application and library modules. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.compileSdk = SahyogSdk.COMPILE
    extension.defaultConfig.minSdk = SahyogSdk.MIN
    extension.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    extension.compileOptions.sourceCompatibility = JAVA_VERSION
    extension.compileOptions.targetCompatibility = JAVA_VERSION
    configureKotlinCompiler()
}

internal fun Project.configureKotlinCompiler() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(KOTLIN_JVM_TARGET)
        }
    }
}
