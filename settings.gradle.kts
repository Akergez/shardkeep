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
            name = "TresseGitHub"
            url = uri(providers.environmentVariable("TRESSE_KMP_MAVEN_URL")
                .orElse("https://maven.pkg.github.com/akergez/tresse-kmp").get())
            // GitHub Packages asks for a token even for a public package: gpr.user and gpr.key
            // (a token with read:packages) in ~/.gradle/gradle.properties, or the CI's own.
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
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
