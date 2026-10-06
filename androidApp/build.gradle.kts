import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val applicationVersionName = providers.environmentVariable("APP_VERSION")
    .orElse("1.0.0")

// v2.3.4 -> 2003004: grows with every tag, so a new build always installs over an older one.
val applicationVersionCode = applicationVersionName.map { raw ->
    val parts = raw.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() }
    if (parts.size <= 3 && parts.all { it != null && it in 0..999 }) {
        (parts.filterNotNull() + listOf(0, 0)).take(3)
            .fold(0) { code, part -> code * 1000 + part }
            .coerceAtLeast(1)
    } else {
        1
    }
}

apply(plugin = "org.jetbrains.kotlin.android")

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.tresse.kmp)
    implementation(libs.native.colors)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.jna) { artifact { type = "aar" } }
}

android {
    namespace = "com.akergez.shardkeep"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
            }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.akergez.shardkeep"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = applicationVersionCode.get()
        versionName = applicationVersionName.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
