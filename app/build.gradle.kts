plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.itexpert120.yomu"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.itexpert120.yomu"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // JNA (via the Convex client) also ships armeabi/mips libraries no supported device can load.
        // Release ships ARM only; debug and benchmark add the x86 ABIs below so emulators still work.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        // Sync backend (backend/convex). Debug builds use the Convex dev deployment, release (and
        // benchmark, which inherits it) the production one. URLs aren't secrets: access is gated by
        // the sync phrase and all records are end-to-end encrypted. Empty = sync unavailable.
        debug {
            buildConfigField("String", "SYNC_CONVEX_URL", "\"https://industrious-dogfish-192.eu-west-1.convex.cloud\"")
            ndk { abiFilters += listOf("x86_64", "x86") }
        }
        release {
            buildConfigField("String", "SYNC_CONVEX_URL", "\"https://colorful-kudu-684.convex.cloud\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            ndk { abiFilters += listOf("x86_64", "x86") }
        }
        // The Baseline Profile plugin copies release into these for profiling; like `benchmark`,
        // they must also run on x86 emulators.
        configureEach {
            if (name == "nonMinifiedRelease" || name == "benchmarkRelease") {
                ndk { abiFilters += listOf("x86_64", "x86") }
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")
    lint {
        // OldTargetApi: we deliberately target the latest STABLE SDK (36); 37 is a preview.
        // VectorPath: the app icon / wordmark is a single detailed logo with a long path — expected.
        disable += setOf("OldTargetApi", "VectorPath")
    }
    // Unit tests run code that logs through android.util.Log (e.g. the sync engine).
    testOptions.unitTests.isReturnDefaultValues = true
    compileSdkMinor = 1
}

kotlin {
    compilerOptions {
        // The UI is built on Material 3 Expressive, whose shape/morph APIs are still experimental.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

hilt {
    // Aggregate Hilt roots via KSP instead of the separate javac task. That javac task bundles
    // kotlin-metadata-jvm capped at metadata 2.3.0 and chokes on Kotlin 2.4.0 classes; KSP reads
    // 2.4.0 metadata natively.
    enableAggregatingTask = false
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigationevent)
    // Installs the bundled Baseline Profile on sideloaded installs (no Play cloud profiles).
    implementation(libs.androidx.profileinstaller)
    // WebView async start-up (reader warm-up); also what Readium uses.
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.window)
    baselineProfile(project(":benchmark"))
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.convex.android)
    constraints {
        // Convex pulls JNA 5.14, whose x86_64 library isn't 16 KB page-aligned (Play requires it).
        implementation(libs.jna) { because("16 KB page-size alignment") }
    }
    // Sync pairing: QR display and decoding (zxing) and an in-app camera scanner (CameraX). No Play
    // services or ML Kit: its component registration breaks under R8.
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":reader-engine"))
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.androidx.media3.session)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.swipe)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
