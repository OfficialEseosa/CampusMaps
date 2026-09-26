package com.campusmaps.data.campus

import com.campusmaps.data.model.Building
import com.campusmaps.data.model.CardOnlyWindow
import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.GeoAnchor
import com.campusmaps.data.model.GraphEdge
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.NodeKind
import com.campusmaps.data.model.Point

// A tiny helper that makes building files easy to read and write by hand.
// Example:
//   corridor("atrium", "Atrium north", floor = 1, x = 40, y = 20)
//   walk("lobby", "atrium")               // length is worked out from the positions
//   walk("p1", "main", lengthM = 12.0)    // outdoor paths can bend, so give the real length
class BuildingBuilder(
    private val id: String,
    private val code: String,
    private val name: String,
    private val floors: IntRange,
    private val geo: GeoAnchor,
) {
    private val nodes = linkedMapOf<String, GraphNode>()
    private val edges = mutableListOf<GraphEdge>()
    private val cardOnly = mutableMapOf<String, List<CardOnlyWindow>>()
    private val demoDestinations = mutableListOf<String>()
    private val starts = mutableListOf<String>()
    private var defaultStart: String? = null

    private fun add(node: GraphNode) {
        require(node.id !in nodes) { "Duplicate node id ${node.id} in $id" }
        nodes[node.id] = node
    }

    fun outdoor(id: String, name: String, x: Number, y: Number, isDefaultStart: Boolean = false) {
        add(GraphNode(id, name, NodeKind.OUTDOOR, floor = floors.first, position = Point(x.toDouble(), y.toDouble())))
        starts += id
        if (isDefaultStart) defaultStart = id
    }

    fun entrance(id: String, name: String, floor: Int, x: Number, y: Number, hint: String? = null) =
        add(GraphNode(id, name, NodeKind.ENTRANCE, floor, Point(x.toDouble(), y.toDouble()), hint = hint))

    fun corridor(id: String, name: String, floor: Int, x: Number, y: Number, sign: String? = null, hint: String? = null, isStart: Boolean = false) {
        add(GraphNode(id, name, NodeKind.CORRIDOR, floor, Point(x.toDouble(), y.toDouble()), signText = sign, hint = hint))
        if (isStart) starts += id
    }

    fun door(id: String, name: String, floor: Int, x: Number, y: Number) =
        add(GraphNode(id, name, NodeKind.DOOR, floor, Point(x.toDouble(), y.toDouble())))

    // A room door. insideX/insideY is a point just inside the room (used for left / right).
    fun room(id: String, name: String, floor: Int, x: Number, y: Number, insideX: Number?, insideY: Number?, demo: Boolean = false) {
        val inside = if (insideX != null && insideY != null) Point(insideX.toDouble(), insideY.toDouble()) else null
        add(GraphNode(id, name, NodeKind.ROOM, floor, Point(x.toDouble(), y.toDouble()), roomInside = inside, signText = name.uppercase()))
        if (demo) demoDestinations += id
    }

    // An elevator shaft: one lobby node per floor, all at the same spot, joined floor to floor.
    // Node ids come out as "<prefix><floor>", e.g. "elev1", "elev2".
    fun elevator(prefix: String, x: Number, y: Number, floorsServed: IntRange = floors) {
        for (f in floorsServed) {
            add(GraphNode("$prefix$f", "Elevator lobby", NodeKind.ELEVATOR, f, Point(x.toDouble(), y.toDouble()), signText = "ELEVATORS"))
        }
        for (f in floorsServed.first until floorsServed.last) {
            edges += GraphEdge("$prefix$f", "$prefix${f + 1}", EdgeKind.ELEVATOR, lengthM = 0.0)
        }
    }

    // A stair shaft, same idea as elevator().
    fun stairs(prefix: String, label: String, x: Number, y: Number, floorsServed: IntRange = floors) {
        for (f in floorsServed) {
            add(GraphNode("$prefix$f", label, NodeKind.STAIRS, f, Point(x.toDouble(), y.toDouble()), signText = label.uppercase()))
        }
        for (f in floorsServed.first until floorsServed.last) {
            edges += GraphEdge("$prefix$f", "$prefix${f + 1}", EdgeKind.STAIRS, lengthM = 0.0)
        }
    }

    // A walkable connection. Without a length we use the straight line distance.
    fun walk(a: String, b: String, lengthM: Double? = null) {
        val na = nodes[a] ?: error("Unknown node $a in $id")
        val nb = nodes[b] ?: error("Unknown node $b in $id")
        edges += GraphEdge(a, b, EdgeKind.WALK, lengthM ?: na.position.distanceTo(nb.position))
    }

    // Chains several nodes: walkChain("a", "b", "c") is walk(a, b) then walk(b, c).
    fun walkChain(vararg ids: String) {
        for (i in 0 until ids.size - 1) walk(ids[i], ids[i + 1])
    }

    // Sets the order of the demo destination rows on S1 (overrides the demo = true order).
    fun demoOrder(vararg ids: String) {
        demoDestinations.clear()
        demoDestinations += ids
    }

    fun cardOnly(entranceId: String, vararg windows: CardOnlyWindow) {
        cardOnly[entranceId] = windows.toList()
    }

    fun build(): Building {
        val start = defaultStart ?: starts.firstOrNull() ?: error("Building $id needs at least one start")
        return Building(
            id = id,
            code = code,
            name = name,
            floors = floors,
            nodes = nodes.toMap(),
            edges = edges.toList(),
            cardOnly = cardOnly.toMap(),
            demoDestinationIds = demoDestinations.toList(),
            startIds = starts.toList(),
            defaultStartId = start,
            geo = geo,
        )
    }
}

fun building(
    id: String,
    code: String,
    name: String,
    floors: IntRange,
    geo: GeoAnchor,
    block: BuildingBuilder.() -> Unit,
): Building = BuildingBuilder(id, code, name, floors, geo).apply(block).build()
