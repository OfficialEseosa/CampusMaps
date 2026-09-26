package com.campusmaps.guidance

import com.campusmaps.data.model.Point
import com.campusmaps.route.Route
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
// Today: loc/SwitchablePositionProvider = loc/ArPositionProvider (ARCore camera pose, once placed) or SimulatedPositionProvider (walks the route by itself, for demos, debug and the emulator).
// ArPositionProvider ignores follow().
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
    // Debug jump off the route (or outside): stand here; the caller reroutes.
    fun placeAt(position: Point, floor: Int)
    var speedMultiplier: Double
    // Debug fake walk: stop the continuous walker (Step and Walk-every-N-s move node by node instead).
    var paused: Boolean
    // Debug "Low confidence" switch (Raphael's): confidence stays at 0.2 until switched off.
    var lowConfidence: Boolean
}

// What the confidence threshold means for the UI (section 6: "Locate me state").
const val LOCATE_CONFIDENCE = 0.5f
