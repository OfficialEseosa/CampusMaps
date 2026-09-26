package com.campusmaps.ui.map

import com.campusmaps.BuildConfig

// Teammate's MapConfig, restored for the Explore map. The key comes from MAPS_API_KEY in local.properties at build time
// (app/build.gradle.kts puts it in BuildConfig and the manifest). Without it the map tiles stay blank and the route is
// a straight line; nothing else changes.
object MapConfig {
    val apiKey: String get() = BuildConfig.MAPS_API_KEY
    val googleAvailable: Boolean get() = apiKey.isNotBlank()
}
