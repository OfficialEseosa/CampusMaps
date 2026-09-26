package com.campusmaps.data.model

import java.time.LocalDateTime
import kotlin.math.hypot

// A position on a floor plan, in metres.
// x grows to the east (right on the map) and y grows to the south (down on the map),
// the same way screen pixels work, so drawing a floor plan needs no flipping.
// NOTE: core building files use y growing NORTH. CoreBridge converts with y = -y (x unchanged).
data class Point(val x: Double, val y: Double) {
    fun distanceTo(other: Point): Double = hypot(other.x - x, other.y - y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun times(k: Double) = Point(x * k, y * k)
}

// What kind of place a graph node is. The router and the step writer use this.
enum class NodeKind {
    OUTDOOR,   // A spot outside the building, like a parking deck or bus stop
    ENTRANCE,  // A building door to the outside. Can be card-only at some times.
    CORRIDOR,  // A named spot inside, like "Atrium north"
    ELEVATOR,  // Elevator lobby on one floor
    STAIRS,    // Stair landing on one floor
    DOOR,      // An inside door the student walks through
    ROOM,      // A room door. Rooms are the destinations.
}

// One point in the building graph.
data class GraphNode(
    val id: String,
    val name: String,
    val kind: NodeKind,
    val floor: Int,
    val position: Point,
    // For rooms: a point just inside the room. Lets us say "on your left / right".
    // Null means we do not know which side the door is on.
    val roomInside: Point? = null,
    // Text printed on a real sign here (used by sign recognition and glasses mode).
    val signText: String? = null,
    // Optional extra words for the instruction, e.g. "doors on the left".
    val hint: String? = null,
) {
    val isOutdoor: Boolean get() = kind == NodeKind.OUTDOOR
}

// How you move along an edge.
enum class EdgeKind { WALK, ELEVATOR, STAIRS }

// Where an edge came from. STUDENT edges are approved shortcuts (section 16 of the handoff).
enum class EdgeSource { MAP, STUDENT }

// A connection between two nodes. Edges work in both directions.
data class GraphEdge(
    val from: String,
    val to: String,
    val kind: EdgeKind,
    val lengthM: Double,
    val source: EdgeSource = EdgeSource.MAP,
    // For STUDENT edges: the name students gave the shortcut.
    val shortcutName: String? = null,
)

// A place outside the building where a trip can start (core StartPoint, or the building origin when the file has none).
// Routing from it uses core's Start.Outside(lat, lng).
data class OutdoorStart(val lat: Double, val lng: Double)

// Everything the screens know about one building: a drawing-friendly view of a core building file
// (app/src/main/assets/buildings/<code>.json), built by data/campus/CoreBridge.kt.
// The core Building stays the source of truth for routing and access rules.
data class Building(
    val id: String,          // The building code, e.g. "CS" (same as core Building.code)
    val code: String,        // Short tag shown in the app bar, e.g. "CS"
    val name: String,        // Name on the S1 selector, e.g. "Classroom South"
    val floors: IntRange,
    val nodes: Map<String, GraphNode>,
    val edges: List<GraphEdge>,
    val demoDestinationIds: List<String>,
    val startIds: List<String>,                      // Choices for "Where are you?": outdoor start points first
    val defaultStartId: String,
    val outdoorStarts: Map<String, OutdoorStart>,    // OUTDOOR node id -> where it is on Earth
    val core: com.campusmaps.data.Building,
) {
    fun node(id: String): GraphNode = nodes.getValue(id)

    val rooms: List<GraphNode> get() = nodes.values.filter { it.kind == NodeKind.ROOM }
    val entrances: List<GraphNode> get() = nodes.values.filter { it.kind == NodeKind.ENTRANCE }

    // Card-only right now? Uses core's access windows (public if any public window covers now).
    fun isCardOnly(entranceId: String, time: LocalDateTime): Boolean =
        core.nodeOrNull(entranceId)?.let { com.campusmaps.data.Access.isLocked(it, time) } ?: false
}
