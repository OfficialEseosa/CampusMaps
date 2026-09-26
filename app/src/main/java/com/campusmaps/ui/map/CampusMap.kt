package com.campusmaps.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.Point
import com.campusmaps.route.Route

// Everything a map needs to draw one frame.
data class MapViewState(
    val building: Building,
    val floor: Int,                 // Floor to draw (the student's floor)
    val route: Route?,
    val user: Point?,
    val userHeadingRad: Double?,
    val arrived: Boolean,
    val outdoors: Boolean,          // True while walking to the entrance
)

// The one map composable the screens use. Always the offline floor plan renderer: the Google Maps outdoor
// renderer was removed at integration (owner decision, docs/21-integration-notes.md); the outdoor leg
// is ARCore Geospatial per the plan (docs/05), drawn here as a straight line to the entrance.
@Composable
fun CampusMap(state: MapViewState, modifier: Modifier = Modifier, compact: Boolean = true) {
    FloorPlanMap(state, modifier, compact)
}
