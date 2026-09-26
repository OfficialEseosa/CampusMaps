package com.campusmaps.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.campusmaps.BuildConfig
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.Point
import com.campusmaps.routing.Route

// Everything a map needs to draw one frame. Renderer independent on purpose:
// the floor plan renderer and the Google Maps renderer read the same state.
data class MapViewState(
    val building: Building,
    val floor: Int,                 // Floor to draw (the student's floor)
    val route: Route?,
    val user: Point?,
    val userHeadingRad: Double?,
    val arrived: Boolean,
    val outdoors: Boolean,          // True while walking to the entrance
)

// Which renderer draws the map.
enum class MapRenderer {
    FLOOR_PLAN, // Offline, drawn on Canvas. Always available.
    GOOGLE,     // Google Maps. Only when MAPS_API_KEY is set in local.properties.
}

object MapConfig {
    // Google Maps is only switched on when a key was provided at build time.
    val googleAvailable: Boolean get() = BuildConfig.MAPS_API_KEY.isNotBlank()

    // Indoors we always use the floor plan (Google has no indoor plan for these buildings).
    // Outdoors we use Google when it is available.
    fun rendererFor(state: MapViewState): MapRenderer =
        if (state.outdoors && googleAvailable) MapRenderer.GOOGLE else MapRenderer.FLOOR_PLAN
}

// The one map composable the screens use. Picks a renderer and draws.
@Composable
fun CampusMap(state: MapViewState, modifier: Modifier = Modifier, compact: Boolean = true) {
    when (MapConfig.rendererFor(state)) {
        MapRenderer.FLOOR_PLAN -> FloorPlanMap(state, modifier, compact)
        MapRenderer.GOOGLE -> GoogleOutdoorMap(state, modifier)
    }
}
