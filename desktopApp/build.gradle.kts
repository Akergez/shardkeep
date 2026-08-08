import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("de.mobanisto.pinpit") version "0.10.0"
}

val applicationVersion = providers.environmentVariable("APP_VERSION")
    .orElse(providers.environmentVariable("CI_COMMIT_TAG"))
    .map { raw ->
        raw.removePrefix("v").substringBefore('-')
            .takeIf { it.matches(Regex("[0-9]+(\\.[0-9]+){0,2}")) }
            ?: "1.0.0"
    }
    .orElse("1.0.0")

val attributeUsage = Attribute.of("org.gradle.usage", String::class.java)

val currentOs: Configuration by configurations.creating {
    extendsFrom(configurations.implementation.get())
    attributes { attribute(attributeUsage, "java-runtime") }
}

val windowsX64: Configuration by configurations.creating {
    extendsFrom(configurations.implementation.get())
    attributes { attribute(attributeUsage, "java-runtime") }
}

val macosArm64: Configuration by configurations.creating {
    extendsFrom(configurations.implementation.get())
    attributes { attribute(attributeUsage, "java-runtime") }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.tresse.kmp)
    implementation(libs.native.colors)

    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    // Skiko is native, so each target gets the artifact for its own platform.
    currentOs(compose.desktop.currentOs)
    windowsX64(compose.desktop.windows_x64)
    macosArm64(compose.desktop.macos_arm64)
}

sourceSets {
    main {
        resources.srcDir("icons")
        java {
            compileClasspath = currentOs
            runtimeClasspath = currentOs
        }
    }
}

// Windows packaging runs through pinpit so the MSI can be built on a Linux runner; macOS and
// Linux keep using jpackage through the Compose plugin.
pinpit.desktop {
    application {
        mainClass = "com.akergez.shardkeep.MainKt"

        nativeDistributions {
            jvmVendor = "adoptium"
            jvmVersion = "21.0.5+11"

            packageName = "Shardkeep"
            packageVersion = applicationVersion.get()
            vendor = "Akergez"
            // Обязательны именно у pinpit: в Compose plugin те же свойства опциональные,
            // а форк требует их значений на этапе конфигурации задачи.
            description = "Shardkeep"
            copyright = "© Akergez"

            windows {
                dirChooser = true
                perUserInstall = true
                shortcut = true
                // Identifies the product across versions, so an install replaces the previous
                // one instead of sitting next to it. It must never change.
                upgradeUuid = "6F3B1C2E-9D4A-4F7B-8E1D-2A5C7B9E4D30"
                packageVersion = applicationVersion.get()
                iconFile.set(project.file("icons/icon.ico"))
                msi {
                    arch = "x64"
                }
            }
            // Nothing is packaged for macOS here, but pinpit validates the block regardless.
            macOS {
                packageName = "Shardkeep"
                bundleID = "com.akergez.shardkeep"
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.akergez.shardkeep.MainKt"

        nativeDistributions {
            // Windows is packaged by pinpit above; jpackage only covers what needs its own host.
            targetFormats(TargetFormat.Dmg, TargetFormat.Rpm)
            packageName = "Shardkeep"
            packageVersion = applicationVersion.get()
            linux {
                iconFile.set(project.file("icons/icon.png"))
                shortcut = true
            }
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
                bundleID = "com.akergez.shardkeep"
            }
        }
    }
}
