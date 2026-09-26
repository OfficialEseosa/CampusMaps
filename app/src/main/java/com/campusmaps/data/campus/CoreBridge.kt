package com.campusmaps.data.campus

import android.content.Context
import android.util.Log
import com.campusmaps.BuildConfig
import com.campusmaps.data.BuildingLoader
import com.campusmaps.data.BuildingValidator
import com.campusmaps.data.Geo
import com.campusmaps.data.NodeType
import com.campusmaps.data.Severity
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.GraphEdge
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.NodeKind
import com.campusmaps.data.model.OutdoorStart
import com.campusmaps.data.model.Point
import com.campusmaps.data.EdgeKind as CoreEdgeKind
import com.campusmaps.data.Building as CoreBuilding

// The adapter between Raphael's core building files and the teammate's screens.
//
// Source of truth: app/src/main/assets/buildings/<code>.json, parsed by core's BuildingLoader and checked by
// core's BuildingValidator (debug builds log every ERROR / WARN / INFO under the "BuildingRepository" tag).
// Each core Building is then turned into the teammate's drawing-friendly Building / GraphNode / GraphEdge model:
//
//   core                          app model (this adapter)
//   y grows NORTH                 y grows SOUTH (screen style): Point(x, -y)
//   entrance                      ENTRANCE
//   intersection, waypoint        CORRIDOR
//   stairs / elevator / room      STAIRS / ELEVATOR / ROOM
//   startPoints (P1, P2)          OUTDOOR nodes (routed with core Start.Outside(lat, lng))
//   no startPoints (KL, CSE)      one synthetic OUTDOOR node "OUT" at the building origin (as Raphael's app did)
//   anchor text / aliases         GraphNode.signText (what the glasses / sign reader would see)
//   room doorFacing               GraphNode.roomInside (a point 2 m behind the door)
//   hallway / door / outdoor      WALK; stairs -> STAIRS; elevator -> ELEVATOR
//   access windows                read live through core Access (Building.isCardOnly)
//
// Nothing here routes: see route/CoreRouter.kt.
object CoreBridge {
    const val DEFAULT_BUILDING_ID = "KL"
    const val TAG = "BuildingRepository"
    const val OUTSIDE_ORIGIN_ID = "OUT"

    // S1 selector order and the short names that fit a three-way segmented button.
    private val ORDER = listOf("KL", "CS", "CSE")
    private val SHORT_NAMES = mapOf("KL" to "Klaus", "CS" to "Classroom South", "CSE" to "Student Center East")

    data class Loaded(
        val buildings: List<Building>,
        // ERROR and WARN validator problems per building code (INFO goes to Logcat only, docs/20 QA #17).
        val problems: Map<String, List<String>>,
    )

    // Loads every assets/buildings/*.json. A file that fails to parse is logged and skipped, never fatal.
    fun load(context: Context): Loaded {
        val assets = context.assets
        val files = assets.list(DIR).orEmpty().filter { it.endsWith(".json") }.sorted()
        val buildings = mutableListOf<Building>()
        val problems = linkedMapOf<String, List<String>>()
        for (f in files) {
            val code = f.removeSuffix(".json")
            try {
                val core = assets.open("$DIR/$f").bufferedReader().use { BuildingLoader.fromJson(it.readText()) }
                if (BuildConfig.DEBUG) {
                    val all = BuildingValidator.validate(core, imageExists = { path -> assetExists(context, path) })
                    all.forEach {
                        when (it.severity) {
                            Severity.ERROR -> Log.e(TAG, "${core.code}: $it")
                            Severity.WARN -> Log.w(TAG, "${core.code}: $it")
                            Severity.INFO -> Log.i(TAG, "${core.code}: $it")
                        }
                    }
                    val shown = all.filter { it.severity != Severity.INFO }.map { it.toString() }
                    if (shown.isNotEmpty()) problems[core.code] = shown
                    Log.i(TAG, "${core.code}: ${core.nodes.size} nodes, ${core.edges.size} edges, ${core.anchors.size} anchors, " +
                        "${all.count { it.severity == Severity.ERROR }} errors, ${all.count { it.severity == Severity.WARN }} warnings")
                }
                buildings += fromCore(core)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot load $f", e)
                problems[code] = listOf("load failed: ${e.message}")
            }
        }
        return Loaded(buildings.sortedBy { ORDER.indexOf(it.id).let { i -> if (i < 0) 99 else i } }, problems)
    }

    private fun assetExists(context: Context, path: String): Boolean = try {
        context.assets.open(path).close(); true
    } catch (_: Exception) {
        false
    }

    private const val DIR = "buildings"

    // Pure conversion, JVM-testable (app/src/test/.../CoreBridgeTest.kt).
    fun fromCore(core: CoreBuilding): Building {
        val nodes = linkedMapOf<String, GraphNode>()
        for (n in core.nodes) {
            nodes[n.id] = GraphNode(
                id = n.id,
                name = n.name,
                kind = kindOf(n.type),
                floor = n.floor,
                position = Point(n.x, -n.y),
                roomInside = if (n.type == NodeType.ROOM) Geo.facingVector(n.doorFacing)?.let { (fx, fy) ->
                    Point(n.x - 2 * fx, -(n.y - 2 * fy))
                } else null,
                signText = core.anchors.firstOrNull { it.node == n.id && (it.text != null || it.aliases.isNotEmpty()) }
                    ?.let { it.text ?: it.aliases.first() },
            )
        }

        // Outdoor starts: the file's start points, or one point at the origin (the building has no surveyed start points).
        val outdoorEntrances = core.nodes.filter { it.isOutdoorEntrance }
        val groundFloor = outdoorEntrances.minOfOrNull { it.floor } ?: core.nodes.minOf { it.floor }
        val outdoorStarts = linkedMapOf<String, OutdoorStart>()
        val points = core.startPoints.ifEmpty {
            val nearest = outdoorEntrances.minByOrNull { kotlin.math.hypot(it.x, it.y) }
            listOf(
                com.campusmaps.data.StartPoint(
                    OUTSIDE_ORIGIN_ID,
                    nearest?.let { "by ${it.name}" } ?: "near the building",
                    core.origin.lat, core.origin.lng, estimated = true,
                ),
            )
        }
        for (p in points) {
            val id = if (nodes.containsKey(p.id)) "OUT-${p.id}" else p.id
            val (x, y) = Geo.toBuilding(core.origin, p.lat, p.lng)
            nodes[id] = GraphNode(id, p.name, NodeKind.OUTDOOR, groundFloor, Point(x, -y))
            outdoorStarts[id] = OutdoorStart(p.lat, p.lng)
        }

        val edges = core.edges.map { e ->
            GraphEdge(
                from = e.from,
                to = e.to,
                kind = when (e.kind) {
                    CoreEdgeKind.STAIRS -> EdgeKind.STAIRS
                    CoreEdgeKind.ELEVATOR -> EdgeKind.ELEVATOR
                    else -> EdgeKind.WALK
                },
                lengthM = e.lengthM,
            )
        } + outdoorStarts.keys.flatMap { startId ->
            // Drawing only (the outdoor leg on the floor plan map). Core adds the real OUTSIDE edges itself.
            outdoorEntrances.map { ent ->
                GraphEdge(startId, ent.id, EdgeKind.WALK, nodes.getValue(startId).position.distanceTo(nodes.getValue(ent.id).position))
            }
        }

        val insideStarts = core.nodes.filter { it.type != NodeType.ROOM }
            .sortedBy { if (it.id.matches(Regex("S\\d+"))) 0 else 1 }
            .map { it.id }
        val rooms = core.nodes.filter { it.type == NodeType.ROOM }.map { it.id }
        val startIds = outdoorStarts.keys.toList() + insideStarts + rooms

        return Building(
            id = core.code,
            code = core.code,
            name = SHORT_NAMES[core.code] ?: core.name,
            floors = core.nodes.minOf { it.floor }..core.nodes.maxOf { it.floor },
            nodes = nodes,
            edges = edges,
            demoDestinationIds = core.demoDestinations.filter { nodes.containsKey(it) },
            startIds = startIds,
            // Demo A (Klaus) starts inside at S1 next to the expo table; every other building starts outside (P1 for CS).
            defaultStartId = if (nodes.containsKey("S1")) "S1" else outdoorStarts.keys.first(),
            outdoorStarts = outdoorStarts,
            core = core,
        )
    }

    private fun kindOf(t: NodeType): NodeKind = when (t) {
        NodeType.ENTRANCE -> NodeKind.ENTRANCE
        NodeType.STAIRS -> NodeKind.STAIRS
        NodeType.ELEVATOR -> NodeKind.ELEVATOR
        NodeType.ROOM -> NodeKind.ROOM
        NodeType.INTERSECTION, NodeType.WAYPOINT -> NodeKind.CORRIDOR
    }
}
