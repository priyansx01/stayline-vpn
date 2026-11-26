import com.android.build.api.variant.FilterConfiguration
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

/** Each ABI's APK gets its own version code: base * 10 + this. F-Droid needs them distinct. */
val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

/**
 * The release key comes from the environment, never from the repo. Without it
 * release builds are unsigned, which is what F-Droid builds and compares
 * against the published APKs.
 */
val releaseKeystore = providers.environmentVariable("STAYLINE_KEYSTORE").orNull?.takeIf { it.isNotBlank() }

android {
    namespace = "io.github.priyansx01.stayline"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "io.github.priyansx01.stayline"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.2.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("STAYLINE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("STAYLINE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("STAYLINE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                releaseKeystore != null -> signingConfigs.getByName("release")
                // For installing a release build while testing: -Pstayline.debugSign=true
                findProperty("stayline.debugSign") == "true" -> signingConfigs.getByName("debug")
                else -> null
            }
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

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
            val code = abiCodes[abi] ?: return@forEach
            output.versionCode.set((output.versionCode.orNull ?: 0) * 10 + code)
        }
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
    // Keep machine-specific paths out of the library, so a build anywhere
    // (here, CI, F-Droid's servers) produces the same bytes. CFLAGS covers
    // the C code some crates compile (ring).
    val root = rootDir.parentFile.absolutePath
    val cargoHome = System.getenv("CARGO_HOME") ?: "${System.getProperty("user.home")}/.cargo"
    environment("RUSTFLAGS", "--remap-path-prefix=$root=/stayline --remap-path-prefix=$cargoHome=/cargo")
    environment("CFLAGS", "-ffile-prefix-map=$root=/stayline -ffile-prefix-map=$cargoHome=/cargo")
    // Build steps on F-Droid's servers do not share a shell, so rustup's
    // directory may not be on PATH yet.
    environment("PATH", "$cargoHome/bin${File.pathSeparator}${System.getenv("PATH")}")
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
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
}
