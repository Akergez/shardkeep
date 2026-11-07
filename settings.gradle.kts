rootProject.name = "Shardkeep"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        if (providers.environmentVariable("TRESSE_KMP_USE_MAVEN_LOCAL").orNull == "true") {
            mavenLocal()
        }
        maven {
            name = "TresseGitHubPackages"
            // Чтение GitHub Packages без авторизации отдаёт 401: для локальной сборки
            // задайте GITHUB_ACTOR и GITHUB_TOKEN (classic PAT с read:packages).
            url = uri(providers.environmentVariable("TRESSE_KMP_MAVEN_URL")
                .orElse("https://maven.pkg.github.com/akergez/tresse-kmp").get())
            credentials {
                username = providers.environmentVariable("TRESSE_GITHUB_ACTOR")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR").orElse(""))
                    .get()
                password = providers.environmentVariable("TRESSE_GITHUB_TOKEN")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN").orElse(""))
                    .get()
            }
            content { includeGroup("com.akergez.tresse") }
        }
        maven {
            name = "NativeColorsGitLab"
            url = uri(providers.environmentVariable("NATIVE_COLORS_MAVEN_URL")
                .orElse("https://gitlab.com/api/v4/projects/85136341/packages/maven").get())
            content { includeGroup("com.akergez.nativecolors") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")
