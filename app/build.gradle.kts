import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Read local.properties so the Google Maps key never goes into git (teammate's original wiring, restored).
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY", "")

android {
    namespace = "com.campusmaps"
    // 37, not 36: SceneView 4.38.0 (AR layer) requires compileSdk 37. targetSdk stays 36. See docs/14.
    compileSdk = 37

    defaultConfig {
        // The watch app uses the same applicationId so the Wear data layer can pair them.
        applicationId = "com.campusmaps"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Meta Wearables Device Access Toolkit: "0" works in Developer Mode (no attestation). See docs/06.
        manifestPlaceholders["mwdat_application_id"] = "0"
        manifestPlaceholders["mwdat_client_token"] = "0"

        // ARCore Geospatial (outdoor leg): API key from local.properties `ARCORE_API_KEY=...` (never committed).
        // Empty is fine: the outdoor leg then falls back to the banner, the map and the FusedLocation distance.
        val arcoreKey = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }.getProperty("ARCORE_API_KEY").orEmpty()
        manifestPlaceholders["arcoreApiKey"] = arcoreKey
        buildConfigField("boolean", "ARCORE_API_KEY_SET", arcoreKey.isNotBlank().toString())
        // Explore map: empty key = map tiles stay blank and the route is a straight line (no Directions call).
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        // Spoken directions: ElevenLabs key from local.properties `ELEVENLABS_API_KEY=...` (never committed).
        // Empty = the phone's own text to speech, as before.
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${localProps.getProperty("ELEVENLABS_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":shared"))
    // Raphael's building model, loader, validator, router and survey converter (pure Kotlin/JVM).
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // System launch splash (Android 12+ API, backported to minSdk 29). See ui/splash.
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Camera image behind the AR overlay, and the ARCore availability check.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.arcore)
    // SceneView ARScene for the world-locked route arrows (docs/05).
    implementation(libs.sceneview.arsceneview)
    // FusedLocation (40 m hand-off trigger) and ARCore Geospatial's location dependency (outdoor leg).
    implementation(libs.play.services.location)

    // Sends the current step to the watch.
    implementation(libs.play.services.wearable)

    // Explore map (leg 1): Google Maps in Compose, and the user's position from the fused location provider.
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)

    // Ray-Ban Meta glasses: toolkit 0.7.0, the version the glasses' DWA 0.7 accepts (docs/06, real-glasses verification).
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.camera)
    implementation(libs.mwdat.mockdevice)
    // Reads sign text on the glasses stills (docs/03 section 2).
    implementation(libs.mlkit.text.recognition)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
