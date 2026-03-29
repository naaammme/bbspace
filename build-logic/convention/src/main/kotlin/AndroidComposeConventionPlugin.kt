import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            // Compose 配置通过 android {} 块在各模块中手动配置
            // 或者通过 afterEvaluate 动态配置
        }
    }
}
