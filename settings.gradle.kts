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
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")

// Shared tresse sync engine. Sibling checkout by default; override with TRESSE_KMP_DIR.
val tresseKmpDir = file(System.getenv("TRESSE_KMP_DIR") ?: "../tresse-kmp")
check(tresseKmpDir.resolve("build.gradle.kts").isFile) {
    "tresse-kmp not found at $tresseKmpDir; set TRESSE_KMP_DIR"
}
include(":tresse-kmp")
project(":tresse-kmp").projectDir = tresseKmpDir