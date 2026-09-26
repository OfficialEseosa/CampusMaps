package com.campusmaps.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Schema of `assets/buildings/<code>.json`, see docs/02-building-data.md. Units: metres, seconds. */
@Serializable
data class Building(
    val code: String,
    val name: String,
    val origin: Origin,
    val floorHeightM: Double = 3.8,
    val walkingSpeedMps: Double = 1.3,
    val stairsSecondsPerFloor: Double = 22.0,
    val stairsDownSecondsPerFloor: Double = 18.0,
    val elevators: List<ElevatorSpec> = emptyList(),
    val nodes: List<Node>,
    val edges: List<Edge>,
    val anchors: List<Anchor> = emptyList(),
    /** Room ids used by a demo (validation rule 6). Extension to the docs/02 schema. */
    val demoDestinations: List<String> = emptyList(),
    /** Outdoor start points (P1, P2) for Demo B. Extension to the docs/02 schema. */
    val startPoints: List<StartPoint> = emptyList(),
    val notes: String? = null,
) {
    private val byId by lazy { nodes.associateBy { it.id } }
    fun node(id: String): Node = byId[id] ?: error("No node $id in $code")
    fun nodeOrNull(id: String): Node? = byId[id]
    fun elevator(id: String?): ElevatorSpec? = elevators.firstOrNull { it.id == id }
}

/** Building frame: +y points at compass bearing [headingDeg] (0 = north), +x is 90 degrees clockwise from it. */
@Serializable
data class Origin(val description: String, val lat: Double, val lng: Double, val headingDeg: Double = 0.0)

@Serializable
data class ElevatorSpec(
    val id: String,
    val avgWaitSec: Double = 35.0,
    val worstWaitSec: Double = 70.0,
    val secondsPerFloor: Double = 6.0,
    val estimated: Boolean = false,
    val notes: String? = null,
)

@Serializable
enum class NodeType {
    @SerialName("entrance") ENTRANCE, @SerialName("intersection") INTERSECTION, @SerialName("stairs") STAIRS,
    @SerialName("elevator") ELEVATOR, @SerialName("room") ROOM, @SerialName("waypoint") WAYPOINT,
}

@Serializable
/** [CLOSED]: nobody gets in, card or not. A time no window covers is closed too. */
enum class AccessRule { @SerialName("public") PUBLIC, @SerialName("card") CARD, @SerialName("closed") CLOSED }

/** One opening window. [days] like "Mon-Fri", "Sat-Sun", "Sat", "Mon,Wed"; [close] may be before [open] (wraps midnight) or "24:00". */
@Serializable
data class AccessWindow(val days: String, val open: String, val close: String, val rule: AccessRule)

@Serializable
data class Node(
    val id: String,
    val type: NodeType,
    val name: String,
    val floor: Int,
    val x: Double,
    val y: Double,
    val access: List<AccessWindow>? = null,
    val elevatorId: String? = null,
    /** Stairwell family for rule 4; defaults to the id without its trailing "-<floor>". */
    val stairsId: String? = null,
    /** Compass word in the building frame (north = +y): which way the door faces, out of the room. */
    val doorFacing: String? = null,
    /** Outdoor entrances: where the door is on Earth (Geospatial hand-off). Validator rule 11 warns when missing. */
    val lat: Double? = null,
    val lng: Double? = null,
    /**
     * Outdoor entrances: compass bearing (0 = north, clockwise) the door faces OUT, as survey records it (standing on the
     * threshold facing out). The heading you face when walking IN is [walkInHeadingDeg] (this + 180).
     */
    val headingDeg: Double? = null,
    val indoor: Boolean = false,
    val estimated: Boolean = false,
    val notes: String? = null,
) {
    val isOutdoorEntrance: Boolean get() = type == NodeType.ENTRANCE && !indoor
    /** Compass bearing you face when walking IN through this door: [headingDeg] (facing out) turned 180 degrees. */
    val walkInHeadingDeg: Double? get() = headingDeg?.let { ((it + 180.0) % 360.0 + 360.0) % 360.0 }
    /** Vertical family: elevatorId for elevators, stairsId or id prefix for stairs. */
    val family: String get() = elevatorId ?: stairsId ?: id.replace(Regex("-\\d+$"), "")
}

@Serializable
enum class EdgeKind {
    @SerialName("hallway") HALLWAY, @SerialName("door") DOOR, @SerialName("stairs") STAIRS,
    @SerialName("elevator") ELEVATOR, @SerialName("outdoor") OUTDOOR;
    val vertical: Boolean get() = this == STAIRS || this == ELEVATOR
}

@Serializable
data class Edge(
    val from: String,
    val to: String,
    val lengthM: Double,
    val kind: EdgeKind = EdgeKind.HALLWAY,
    val floors: Int = 0,
    val hint: String? = null,
    val oneWay: Boolean = false,
    val estimated: Boolean = false,
    val notes: String? = null,
)

@Serializable
enum class AnchorKind { @SerialName("image") IMAGE, @SerialName("text") TEXT }

@Serializable
data class Anchor(
    val id: String,
    val node: String,
    val kind: AnchorKind,
    val image: String? = null,
    val widthM: Double? = null,
    val x: Double,
    val y: Double,
    val floor: Int,
    val heightM: Double? = null,
    /** Compass word in the building frame (north = +y): which way the sign's face points. */
    val facing: String? = null,
    val text: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val estimated: Boolean = false,
    /** Image anchor whose photo is not taken yet (placeholder): a missing file is a rule 7 WARN instead of an ERROR. */
    val imagePending: Boolean = false,
    val notes: String? = null,
)

@Serializable
data class StartPoint(val id: String, val name: String, val lat: Double, val lng: Double, val estimated: Boolean = false, val notes: String? = null)

/** One traversable direction of an edge. */
data class Adjacent(val to: String, val edge: Edge)

/** Adjacency list honouring [Edge.oneWay]. */
fun Building.graph(): Map<String, List<Adjacent>> {
    val adj = nodes.associate { it.id to mutableListOf<Adjacent>() }
    for (e in edges) {
        adj[e.from]?.add(Adjacent(e.to, e))
        if (!e.oneWay) adj[e.to]?.add(Adjacent(e.from, e))
    }
    return adj
}
