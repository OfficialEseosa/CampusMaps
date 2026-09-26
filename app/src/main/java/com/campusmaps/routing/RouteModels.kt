package com.campusmaps.routing

import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.Point

// How a route changes floor. Drives the method icon on S1b (elevator / stairs / trending_flat).
enum class FloorChange { ELEVATOR, STAIRS, LEVEL }

// One point along a route, in walking order.
data class RoutePoint(
    val node: GraphNode,
    // How we got here from the previous point (WALK for the first point).
    val arrivedBy: EdgeKind,
    // Walking distance from the start of the route to here, in metres.
    // Elevator and stair edges add no walking distance.
    val cumulativeM: Double,
    // True if the edge into this point is an approved student shortcut.
    val arrivedByStudentEdge: Boolean = false,
) {
    val position: Point get() = node.position
    val floor: Int get() = node.floor
}

// What kind of instruction a step is. Also decides the icon in the S2 banner and on the watch.
enum class StepKind {
    WALK_TO_ENTRANCE, // Starting outside: "Walk to Library South entrance"
    HEAD,             // Starting inside: "Head toward Atrium north"
    CONTINUE,         // After a reroute, facing the right way: "Continue toward ..."
    TURN_LEFT,
    TURN_RIGHT,
    TURN_AROUND,
    ELEVATOR,
    STAIRS,
    EXIT_TOWARD,      // Right after leaving the elevator or stairs
    DOOR,             // Inside door
    ARRIVE,
    ALREADY_THERE,
}

// One instruction. The user sees it while walking toward the point where it happens.
data class RouteStep(
    val kind: StepKind,
    val text: String,
    // Index into Route.points where the manoeuvre starts (used for "in 16 m").
    val startIndex: Int,
    // The step is done once the user has walked this far along the route...
    val completeAtM: Double,
    // ...and is on this floor (matters for elevator and stairs rides).
    val completeFloor: Int,
    // Place name used by the watch label and the glasses "Seen" line.
    val place: String? = null,
    // For elevator and stairs: the floor you ride to.
    val targetFloor: Int? = null,
    // For elevator and stairs: the floor the ride starts on (tells stairs up from stairs down).
    val fromFloor: Int? = null,
    // Only for ARRIVE: which side the door is on. Null = unknown.
    val side: Side? = null,
    // Only for WALK_TO_ENTRANCE: the text to show once the student reaches the door.
    val approachText: String? = null,
)

// A full route from start to destination, ready for guidance.
data class Route(
    val buildingId: String,
    val points: List<RoutePoint>,
    val steps: List<RouteStep>,
    val destination: GraphNode,
) {
    val totalWalkM: Double get() = points.lastOrNull()?.cumulativeM ?: 0.0
    val startsOutside: Boolean get() = points.firstOrNull()?.node?.isOutdoor == true
}

// One card on S1b.
data class RouteOption(
    val id: String,
    val entrance: GraphNode?,       // Null when starting inside
    val method: FloorChange,
    val etaSeconds: Double,
    val walkM: Double,
    val floorsChanged: Int,         // Absolute number of floors ridden or climbed
    val elevatorWaitS: Int?,        // Average wait, only for elevator routes
    val usesStudentShortcut: Boolean,
    val shortcutName: String?,
    val alsoVia: List<String>,      // Other entrances that are almost as fast
    val route: Route,
)

// The "Heads up" banner on S1b. Information, never an error.
data class LockedNotice(val lockedEntrance: String, val usingEntrance: String?) {
    val text: String get() = Instructions.lockedNotice(lockedEntrance, usingEntrance)
}

// Everything the router can answer.
sealed interface RoutePlan {
    data class Options(val options: List<RouteOption>, val lockedNotice: LockedNotice?) : RoutePlan
    data class AlreadyHere(val room: GraphNode, val route: Route) : RoutePlan
    data class NoRoute(val message: String) : RoutePlan
}
