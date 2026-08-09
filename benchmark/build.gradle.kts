plugins {
    id("com.android.test")
}

android {
    namespace = "com.itexpert120.yomu.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        create("release") {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.rules)
    implementation(libs.androidx.uiautomator)
}
