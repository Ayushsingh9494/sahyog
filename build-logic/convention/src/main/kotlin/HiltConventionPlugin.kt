import com.sahyog.buildlogic.lib
import com.sahyog.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `sahyog.hilt`: adds Hilt with KSP code generation.
 * - Android modules get the Hilt Gradle plugin + hilt-android.
 * - Pure JVM modules get hilt-core, so they can still contribute @Module bindings.
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        dependencies {
            add("ksp", libs.lib("hilt-compiler"))
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            dependencies { add("implementation", libs.lib("hilt-core")) }
        }

        listOf("com.android.application", "com.android.library").forEach { androidPlugin ->
            pluginManager.withPlugin(androidPlugin) {
                pluginManager.apply("com.google.dagger.hilt.android")
                dependencies { add("implementation", libs.lib("hilt-android")) }
            }
        }
    }
}
