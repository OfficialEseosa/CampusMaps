package com.campusmaps.route

// View models of a route, as the teammate's screens, GuidanceEngine, watch and glasses flows expect them.
// They are no longer computed here: CoreRouter builds them from core's Router.route(...) output
// (Raphael's core module is the source of truth for paths, ETAs, entrances, notices and instruction text).

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
    // Outdoor street steps from Google Directions (Explore start only; outdoor/StreetSteps.kt), followed by GPS.
    STREET_LEFT,
    STREET_RIGHT,
    STREET_STRAIGHT,
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
    // Outdoor steps of an Explore start: where the step ends (lat/lng). The step is then done by GPS, not by the walker
    // (guidance/OutdoorGps.kt). Null for every indoor step and for routes from S1b's fixed start points.
    val outdoorEnd: com.campusmaps.outdoor.LatLngPoint? = null,
)

// A full route from start to destination, ready for guidance.
data class Route(
    val buildingId: String,
    val points: List<RoutePoint>,
    val steps: List<RouteStep>,
    val destination: GraphNode,
    // Core node ids in walking order (Router.OUTSIDE replaced by the outdoor start node id).
    val nodeIds: List<String> = points.map { it.node.id },
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
    // The core option this card was built from (node ids, ETA breakdown, core instructions). For debug and the AR layer.
    val core: com.campusmaps.routing.RouteOption? = null,
)

// The "Heads up" banner on S1b. Information, never an error.
// Built from core's RouteOption.notice ("Heads up: Main entrance is card-only now. Using West entrance instead.").
data class LockedNotice(val lockedEntrance: String, val usingEntrance: String?, val text: String) {
    companion object {
        private val PATTERN = Regex("""^(?:Heads up: )?(.+?) is card-only now\. (?:Using (.+) instead\.|Taking another way\.)$""")

        fun fromCore(notice: String): LockedNotice {
            val text = notice.removePrefix("Heads up: ").trim()
            val m = PATTERN.find(notice.trim()) ?: return LockedNotice(text, null, text)
            return LockedNotice(m.groupValues[1], m.groupValues[2].ifBlank { null }, text)
        }
    }
}

// Everything the router can answer.
sealed interface RoutePlan {
    data class Options(val options: List<RouteOption>, val lockedNotice: LockedNotice?) : RoutePlan
    data class AlreadyHere(val room: GraphNode, val route: Route) : RoutePlan
    data class NoRoute(val message: String) : RoutePlan
}
