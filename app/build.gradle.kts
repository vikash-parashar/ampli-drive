plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vikash.ampli.drive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vikash.ampli.drive"
        // 26 (Android 8.0): many head units report "Android 12+" in About but really run Android 8 to 11.
        // Newer APIs are guarded with SDK_INT checks or androidx compat; lint fails the build on NewApi.
        minSdk = 26
        targetSdk = 35
        // CI passes the run number so each test build installs as an update over the last one.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        // Ampli owner server for Help & feedback. CI fills it from the OWNER_API_URL repository
        // variable; empty means "no server yet" and the app falls back to an email.
        val ownerApi = (System.getenv("OWNER_API_URL") ?: "").trim().replace("\"", "")
        buildConfigField("String", "OWNER_API_URL", "\"$ownerApi\"")

        // In-app navigation routes (OSRM HTTP API). CI fills it from the ROUTING_BASE_URL
        // repository variable. Without it, the OSRM project's public demo server is used, which is
        // for TESTING ONLY (no SLA, ~1 request/s, not allowed for production apps): point this at
        // your own OSRM server before release.
        val routingBase = (System.getenv("ROUTING_BASE_URL") ?: "").trim().replace("\"", "")
            .ifEmpty { "https://router.project-osrm.org" }
        buildConfigField("String", "ROUTING_BASE_URL", "\"$routingBase\"")

        // Head units are ARM. Dropping the x86 MapLibre libraries keeps the APK much smaller.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // One fixed key for test APKs. Without it every CI machine signs with a new random key,
    // and Android refuses to install the next build over the previous one.
    // For test builds only: Play Store releases use your own upload key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("test-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // CI runs lintDebug: any call to an API newer than minSdk without a version check fails the
    // build, so head units on Android 8 to 11 can't crash on a missing method.
    lint {
        checkOnly += "NewApi"
        error += "NewApi"
        abortOnError = true
        checkReleaseBuilds = false
        textReport = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.maplibre.android)
    implementation(libs.billing.ktx)
    implementation(libs.haze)

    // "Hey Ampli" wake word. The ~36 MB speech model is downloaded at runtime, not bundled.
    implementation(libs.vosk.android)
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    // FastagParser tests: ./gradlew testDebugUnitTest
    testImplementation(libs.junit)
}
