plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

// Значение подставляется на CI из переменной окружения TRESSE_SERVER_URL: им заполнено
// поле сервера на экране синхронизации (пользователь может заменить); пустое — поле пустое.
val generateTresseServerUrl = tasks.register("generateTresseServerUrl") {
    val url = providers.environmentVariable("TRESSE_SERVER_URL")
        .map { it.trim().removeSuffix("/") }
        .orElse("")
    inputs.property("tresseServerUrl", url)
    val outputDir = layout.buildDirectory.dir("generated/tresseServerUrl")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        val escaped = url.get().replace("\\", "\\\\").replace("\"", "\\\"")
        dir.resolve("TresseServerUrl.kt").writeText(
            """
            package com.akergez.shardkeep

            /** Build-time value of TRESSE_SERVER_URL; empty when the build did not set one. */
            const val TRESSE_SERVER_URL: String = "$escaped"

            """.trimIndent(),
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    androidLibrary {
       namespace = "com.akergez.shardkeep.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.sqldelight.android.driver)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Сгенерированная константа подключается снаружи блока kotlin {}: внутри sourceSets {}
// у commonMain.kotlin неоднозначный ресивер и скрипт не компилируется.
kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateTresseServerUrl)

compose.resources {
    publicResClass = true
}

sqldelight {
    databases {
        create("ShardkeepDatabase") {
            packageName.set("com.akergez.shardkeep.db")
        }
    }
}
