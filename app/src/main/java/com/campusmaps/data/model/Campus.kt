package com.campusmaps.data.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.hypot

// A position on a floor plan, in metres.
// x grows to the east (right on the map) and y grows to the south (down on the map),
// the same way screen pixels work, so drawing a floor plan needs no flipping.
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

// A time window when an entrance only opens with a card.
// If "to" is earlier than "from" the window runs past midnight (e.g. 22:00 to 07:00).
data class CardOnlyWindow(
    val days: Set<DayOfWeek>,
    val from: LocalTime,
    val to: LocalTime,
) {
    fun contains(time: LocalDateTime): Boolean {
        val t = time.toLocalTime()
        return if (from <= to) {
            time.dayOfWeek in days && t >= from && t < to
        } else {
            // Wraps midnight: the late part belongs to "today", the early part to "yesterday".
            val lateToday = time.dayOfWeek in days && t >= from
            val earlyFromYesterday = time.dayOfWeek.minus(1) in days && t < to
            lateToday || earlyFromYesterday
        }
    }

    companion object {
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

// Ties the floor plan (metres) to real world coordinates, so a Google map can be drawn later.
// origin is where plan point (0, 0) is on Earth.
data class GeoAnchor(val originLat: Double, val originLng: Double) {
    // Converts plan metres to latitude and longitude. Good enough over a few hundred metres.
    fun toLatLng(p: Point): Pair<Double, Double> {
        val metresPerDegLat = 111_320.0
        val metresPerDegLng = 111_320.0 * cos(Math.toRadians(originLat))
        val lat = originLat - p.y / metresPerDegLat // y grows south
        val lng = originLng + p.x / metresPerDegLng
        return lat to lng
    }
}

// Everything the app knows about one building. This is the "building file".
data class Building(
    val id: String,
    val code: String,        // Short tag shown in the app bar, e.g. "CS"
    val name: String,        // Full name, e.g. "Classroom South"
    val floors: IntRange,
    val nodes: Map<String, GraphNode>,
    val edges: List<GraphEdge>,
    val cardOnly: Map<String, List<CardOnlyWindow>>, // entrance id -> card-only windows
    val demoDestinationIds: List<String>,
    val startIds: List<String>,                      // Choices for "Where are you?"
    val defaultStartId: String,
    val geo: GeoAnchor,
) {
    fun node(id: String): GraphNode = nodes.getValue(id)

    val rooms: List<GraphNode> get() = nodes.values.filter { it.kind == NodeKind.ROOM }
    val entrances: List<GraphNode> get() = nodes.values.filter { it.kind == NodeKind.ENTRANCE }

    fun isCardOnly(entranceId: String, time: LocalDateTime): Boolean =
        cardOnly[entranceId].orEmpty().any { it.contains(time) }
}
