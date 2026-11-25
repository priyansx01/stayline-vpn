import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** ABIs to build; `-Pstayline.abis=arm64-v8a` speeds up development builds. */
val abis = (findProperty("stayline.abis") as String? ?: "arm64-v8a,armeabi-v7a,x86_64")
    .split(',').map { it.trim() }.filter { it.isNotEmpty() }

android {
    namespace = "io.github.priyansx01.stayline"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "io.github.priyansx01.stayline"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Lets a release build be installed for testing; use a real key to publish.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*abis.toTypedArray())
            isUniversalApk = false
        }
    }

    packaging {
        // Compressed native code keeps the APK small.
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Builds crates/mobile into jniLibs. Skip with -Pstayline.skipCargo=true when
// the libraries are already in place.
val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")
val cargoBuild = tasks.register<Exec>("cargoBuild") {
    group = "build"
    description = "Builds the Rust tunnel engine for Android."
    workingDir = rootDir.parentFile
    environment("ANDROID_NDK_HOME", android.ndkDirectory.absolutePath)
    commandLine(
        buildList {
            addAll(listOf("cargo", "ndk"))
            abis.forEach { addAll(listOf("-t", it)) }
            addAll(listOf("-P", "26", "-o", jniLibsDir.asFile.absolutePath))
            addAll(listOf("build", "--release", "-p", "stayline-mobile"))
        },
    )
}
if (findProperty("stayline.skipCargo") != "true") {
    tasks.named("preBuild") { dependsOn(cargoBuild) }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
}
