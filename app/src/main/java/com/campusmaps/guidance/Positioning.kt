package com.campusmaps.guidance

import com.campusmaps.data.model.Point
import com.campusmaps.routing.Route
import kotlinx.coroutines.flow.StateFlow

// Where the student is, as the localisation system sees it.
data class Pose(
    val position: Point,     // Floor plan metres
    val floor: Int,
    val headingRad: Double,  // Direction the phone faces, on the floor plan (0 = east, PI/2 = south)
    val confidence: Float,   // 0..1. Below 0.5 the app asks the student to point at a sign.
    val sign: SignSighting? = null,
)

// A sign the camera is currently reading. Box is in 0..1 screen fractions.
data class SignSighting(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

// The seam between guidance and whatever knows the student's position.
// Today: SimulatedPositionProvider (walks the route by itself, for demos and the emulator).
// Later: ARCore + sign recognition implement this and ignore follow().
interface PositionProvider {
    val pose: StateFlow<Pose>

    // Called whenever a new route starts (including after a reroute).
    fun follow(route: Route)

    fun stop()
}

// Extra controls only the simulator has. The debug overlay and "Fake step" use these.
interface SimulationControls {
    fun dropConfidence()
    fun pushOffRoute()
    fun jumpToPoint(index: Int)
    var speedMultiplier: Double
}

// What the confidence threshold means for the UI (section 6: "Locate me state").
const val LOCATE_CONFIDENCE = 0.5f
