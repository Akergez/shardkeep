import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val applicationVersion = providers.environmentVariable("APP_VERSION")
    .orElse(providers.environmentVariable("CI_COMMIT_TAG"))
    .map { raw ->
        raw.removePrefix("v").substringBefore('-')
            .takeIf { it.matches(Regex("[0-9]+(\\.[0-9]+){0,2}")) }
            ?: "1.0.0"
    }
    .orElse("1.0.0")

dependencies {
    implementation(projects.shared)
    implementation(libs.tresse.kmp)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

sourceSets {
    main {
        resources.srcDir("icons")
    }
}

compose.desktop {
    application {
        mainClass = "com.akergez.shardkeep.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Rpm)
            packageName = "Shardkeep"
            packageVersion = applicationVersion.get()
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
            }
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
                bundleID = "com.akergez.shardkeep"
            }
        }
    }
}
