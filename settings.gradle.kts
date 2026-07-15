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
            name = "TresseGitLab"
            url = uri(providers.environmentVariable("TRESSE_KMP_MAVEN_URL")
                .orElse("https://gitlab.com/api/v4/projects/84164267/packages/maven").get())
            content { includeGroup("com.akergez.tresse") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")
