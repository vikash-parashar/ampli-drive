plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** A CI repository variable (environment), trimmed and without quotes; "" when unset. */
fun env(name: String): String = (System.getenv(name) ?: "").trim().replace("\"", "")

// Release builds must use our own routing and place-search servers (deploy/osrm-photon).
// ALLOW_UNCONFIGURED_SERVERS=true lets a CI smoke test build a release (R8 check) anyway: the app
// then has routing and search switched off, never a public demo server.
val releaseMissing = listOf("ROUTING_BASE_URL", "GEOCODER_URL").filter { env(it).isEmpty() }
val releaseAllowUnconfigured = env("ALLOW_UNCONFIGURED_SERVERS") == "true"
val checkReleaseEndpoints by tasks.registering {
    group = "verification"
    description = "Fails release builds that would use public demo servers."
    val missing = releaseMissing
    val allow = releaseAllowUnconfigured
    doLast {
        if (missing.isNotEmpty() && !allow) {
            val msg = "Release build needs your own servers: set ${missing.joinToString(" and ")} " +
                "(CI repository variables), or ALLOW_UNCONFIGURED_SERVERS=true for a smoke-test-only " +
                "build. See deploy/osrm-photon/README.md."
            // Shows as a check annotation on GitHub, so the reason is visible without the log.
            if (System.getenv("GITHUB_ACTIONS") == "true") println("::error title=Release servers::$msg")
            throw GradleException(msg)
        }
    }
}
tasks.matching { it.name.startsWith("pre") && it.name.endsWith("ReleaseBuild") }.configureEach {
    dependsOn(checkReleaseEndpoints)
}

android {
    namespace = "com.vikash.ampli.drive"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vikash.ampli.drive"
        // 26 (Android 8.0): many head units report "Android 12+" in About but really run Android 8 to 11.
        // Newer APIs are guarded with SDK_INT checks or androidx compat; lint fails the build on NewApi.
        minSdk = 26
        targetSdk = 36
        // CI passes the run number so each test build installs as an update over the last one.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        // Ampli owner server for Help & feedback. CI fills it from the OWNER_API_URL repository
        // variable; empty means "no server yet" and the app falls back to an email.
        buildConfigField("String", "OWNER_API_URL", "\"${env("OWNER_API_URL")}\"")

        // Servers (see deploy/osrm-photon/README.md). CI fills them from repository variables.
        // Release builds get ONLY these values (the build fails without routing and place search);
        // debug builds fall back to the public demo servers below, which are for testing only.
        buildConfigField("String", "WEATHER_API_KEY", "\"${env("WEATHER_API_KEY")}\"")
        // Our own map style (e.g. PMTiles on S3/CloudFront) tried when the theme's style fails.
        buildConfigField("String", "MAP_FALLBACK_STYLE_URL", "\"${env("MAP_FALLBACK_STYLE_URL")}\"")
        // Small JSON on our own server that can move the servers above without an update (RemoteConfig).
        buildConfigField("String", "REMOTE_CONFIG_URL", "\"${env("REMOTE_CONFIG_URL")}\"")
        // Pro launch offer line ("Launch price ₹1,499, later ₹2,500"). Set LAUNCH_OFFER=false when it ends.
        buildConfigField("boolean", "LAUNCH_OFFER", "${env("LAUNCH_OFFER") != "false"}")

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
        // Play upload key (repo secrets UPLOAD_KEYSTORE_BASE64 / _PASSWORD / UPLOAD_KEY_ALIAS /
        // UPLOAD_KEY_PASSWORD; the workflow writes the file). Absent: the release bundle is unsigned.
        if (env("UPLOAD_KEYSTORE_FILE").isNotEmpty()) {
            create("upload") {
                storeFile = file(env("UPLOAD_KEYSTORE_FILE"))
                storePassword = env("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = env("UPLOAD_KEY_ALIAS")
                keyPassword = env("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // Public demo servers for test builds only: OSRM demo, komoot Photon, Open-Meteo free API.
            buildConfigField("String", "ROUTING_BASE_URL", "\"${env("ROUTING_BASE_URL").ifEmpty { "https://router.project-osrm.org" }}\"")
            buildConfigField("String", "GEOCODER_URL", "\"${env("GEOCODER_URL").ifEmpty { "https://photon.komoot.io/api/" }}\"")
            buildConfigField("String", "WEATHER_URL", "\"${env("WEATHER_URL").ifEmpty { "https://api.open-meteo.com/v1/forecast" }}\"")
            // Test APKs only: "Download and install" Ampli MP3 Player from its GitHub releases
            // (src/debug/AndroidManifest.xml adds REQUEST_INSTALL_PACKAGES). Play builds use Play.
            buildConfigField("boolean", "SIDELOAD_INSTALL", "true")
        }
        release {
            // Never a public demo server in a paid app: checkReleaseEndpoints fails the build when
            // routing or place search is missing. Weather stays off until WEATHER_URL is set.
            buildConfigField("String", "ROUTING_BASE_URL", "\"${env("ROUTING_BASE_URL")}\"")
            buildConfigField("String", "GEOCODER_URL", "\"${env("GEOCODER_URL")}\"")
            buildConfigField("String", "WEATHER_URL", "\"${env("WEATHER_URL")}\"")
            // The Play bundle never downloads or installs other APKs (and never declares the permission).
            buildConfigField("boolean", "SIDELOAD_INSTALL", "false")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
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

    // Screenshot tests (app/src/test/.../screens) run on the JVM with Robolectric and save PNGs
    // to app/build/outputs/roborazzi; CI uploads them next to the test APK.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "2g"
                // UPDATE_VOICE_LINES=1 ./gradlew testDebugUnitTest --tests '*VoiceManifestTest'
                // rewrites voice/lines.json (the voice-pack workflow's script) from HaryanviLines.kt.
                it.inputs.property("updateVoiceLines", System.getenv("UPDATE_VOICE_LINES") ?: "")
            }
        }
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
    implementation(libs.media3.exoplayer)
    // Parking-spot QR on the "Where's my car" screen.
    implementation(libs.zxing.core)

    // "Hey Ampli" wake word. The ~36 MB speech model is downloaded at runtime, not bundled.
    implementation(libs.vosk.android)
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    // FastagParser tests: ./gradlew testDebugUnitTest
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// On CI, each failing unit test becomes a check annotation (class, test, first line of the error),
// so a red "Unit tests" step says which test broke without opening the log.
if (System.getenv("GITHUB_ACTIONS") == "true") {
    tasks.withType<Test>().configureEach {
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                if (result.resultType != TestResult.ResultType.FAILURE) return
                val why = result.exception?.toString()?.lineSequence()?.firstOrNull()?.take(300).orEmpty()
                println("::error title=Test failed::${testDescriptor.className}.${testDescriptor.name}: $why")
            }
        })
    }
}
