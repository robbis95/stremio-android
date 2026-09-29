import java.util.Properties
import com.stremio.gradle.PrepareStreamServerArm64
import com.stremio.gradle.VerifyArm64ApkPackaging

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

fun stringPropertyOrEnv(name: String): String? =
    (findProperty(name) as? String)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val supportedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

val releaseSigningValues = listOf(
    stringPropertyOrEnv("ANDROID_KEYSTORE_FILE"),
    stringPropertyOrEnv("ANDROID_KEYSTORE_PASSWORD"),
    stringPropertyOrEnv("ANDROID_KEY_ALIAS"),
    stringPropertyOrEnv("ANDROID_KEY_PASSWORD"),
)
val hasPartialReleaseSigning = releaseSigningValues.any { it != null } && releaseSigningValues.any { it == null }
if (hasPartialReleaseSigning) {
    throw GradleException(
        "Release signing requires ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD.",
    )
}

val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    val stream = localPropertiesFile.inputStream()
    localProperties.load(stream)
    stream.close()
}
val posthogApiKey = (localProperties.getProperty("posthog.apiKey") ?: System.getenv("POSTHOG_API_KEY") ?: "").trim()
val posthogHost = (localProperties.getProperty("posthog.host") ?: System.getenv("POSTHOG_HOST") ?: "https://us.i.posthog.com").trim()

android {
    namespace = "com.stremio.mobile"
    compileSdk = 37
    ndkVersion = "29.0.13846066"

    packaging {
        resources {
            excludes.add("google/protobuf/**")
            excludes.add("META-INF/gradle/incremental.annotation.processors")
        }
    }

    defaultConfig {
        applicationId = "com.stremio.mobile"
        minSdk = 24
        targetSdk = 37
        versionCode = stringPropertyOrEnv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = stringPropertyOrEnv("VERSION_NAME") ?: "0.1.0"
        ndk {
            abiFilters.addAll(supportedAbis)
        }
        buildConfigField("String", "POSTHOG_API_KEY", "\"$posthogApiKey\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$posthogHost\"")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*supportedAbis.toTypedArray())
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (!hasPartialReleaseSigning && releaseSigningValues.all { it != null }) {
            create("release") {
                storeFile = file(releaseSigningValues[0]!!)
                storePassword = releaseSigningValues[1]
                keyAlias = releaseSigningValues[2]
                keyPassword = releaseSigningValues[3]
            }
        }
    }

    buildTypes {
        debug {
            matchingFallbacks.add("release")
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


}

val generatedStreamServerJniLibs = layout.buildDirectory.dir("generated/streamServerJniLibs")

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0-rc01")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0-rc01")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.fragment:fragment:1.5.4")

    implementation("com.facebook.android:facebook-login:18.2.3")

    implementation(files("libs/rustls-platform-verifier-0.1.1.aar"))

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation(project(":mpv-android-lib"))

    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.4.0")
    implementation("pro.streem.pbandk:pbandk-runtime:0.16.0")
    implementation("com.github.Stremio:stremio-core-kotlin:1.15.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.zxing:core:3.5.4")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.posthog:posthog-android:3.51.0")
    testImplementation("junit:junit:4.13.2")
}
val streamServerRoot = rootProject.file("stream-server")
val streamServerArm64Library = streamServerRoot.resolve("target/aarch64-linux-android/release/libstream_server.so")
val nativeBuildScript = rootProject.file(".github/scripts/build-stream-server-android-arm64.sh")
val configuredSdkDir = localProperties.getProperty("sdk.dir")?.let { file(it) }
val configuredNdkHome = stringPropertyOrEnv("ANDROID_NDK_HOME")
    ?: stringPropertyOrEnv("ANDROID_NDK_ROOT")
    ?: configuredSdkDir?.resolve("ndk/29.0.13846066")?.absolutePath
    ?: ""
val configuredVcpkgRoot = stringPropertyOrEnv("VCPKG_ROOT") ?: ""
val configuredVcpkgInstallDir = stringPropertyOrEnv("VCPKG_INSTALLED_DIR_ARM64")
    ?: stringPropertyOrEnv("VCPKG_INSTALLED_DIR")
    ?: layout.buildDirectory.dir("vcpkg_installed_arm64").get().asFile.absolutePath

tasks.register<Exec>("buildStreamServerArm64") {
    group = "native build"
    description = "Builds the pinned libtorrent stream-server for Android arm64-v8a."
    commandLine("bash", nativeBuildScript.absolutePath)
    environment("ANDROID_NDK_HOME", configuredNdkHome)
    environment("VCPKG_ROOT", configuredVcpkgRoot)
    environment("VCPKG_INSTALLED_DIR", configuredVcpkgInstallDir)
    environment("STREAM_SERVER_ROOT", streamServerRoot.absolutePath)
}

val prepareStreamServerArm64 = tasks.register<PrepareStreamServerArm64>("prepareStreamServerArm64") {
    group = "native build"
    description = "Builds and stages the Android arm64-v8a stream-server JNI library under app/build."
    dependsOn("buildStreamServerArm64")
    nativeLibrary.set(streamServerArm64Library)
    outputDirectory.set(generatedStreamServerJniLibs)
}

val nativeTvTaskRequested = gradle.startParameter.taskNames.any { task ->
    task.substringAfterLast(':') in setOf("assembleTvArm64Debug", "verifyArm64StreamServerPackaging")
} || findProperty("includeStreamServerArm64") == "true"
if (nativeTvTaskRequested) {
    androidComponents.onVariants(androidComponents.selector().withName("debug")) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(prepareStreamServerArm64) { it.outputDirectory }
    }
}

tasks.register<VerifyArm64ApkPackaging>("verifyArm64StreamServerPackaging") {
    group = "verification"
    description = "Inspects the arm64 debug APK ZIP for the stream-server and shared C++ runtime."
    dependsOn("assembleDebug")
    apkDirectory.set(layout.buildDirectory.dir("outputs/apk/debug"))
}

tasks.register("assembleTvArm64Debug") {
    group = "build"
    description = "Builds, packages, and verifies the native-enabled ARM64 TV debug APK."
    dependsOn("verifyArm64StreamServerPackaging")
}
