import com.android.build.api.dsl.ApplicationExtension
import com.sahyog.buildlogic.SahyogSdk
import com.sahyog.buildlogic.configureAndroidCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `sahyog.android.application`: the single installable :app module. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig.targetSdk = SahyogSdk.TARGET
        }
    }
}
