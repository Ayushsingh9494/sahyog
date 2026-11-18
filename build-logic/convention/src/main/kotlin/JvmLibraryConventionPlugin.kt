import com.sahyog.buildlogic.JAVA_VERSION
import com.sahyog.buildlogic.configureKotlinCompiler
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/**
 * `sahyog.jvm.library`: pure Kotlin modules with no Android dependency
 * (models, shared utilities). They compile and test faster and can't leak Android types.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }
        configureKotlinCompiler()
    }
}
