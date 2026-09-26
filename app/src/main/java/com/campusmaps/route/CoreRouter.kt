package com.campusmaps.route

import com.campusmaps.data.Access
import com.campusmaps.data.Geo
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.EdgeSource
import com.campusmaps.data.model.GraphEdge
import com.campusmaps.data.model.GraphNode
import com.campusmaps.routing.Direction
import com.campusmaps.routing.Instruction
import com.campusmaps.routing.InstructionType
import com.campusmaps.routing.Prefs
import com.campusmaps.routing.Router
import com.campusmaps.routing.Start
import com.campusmaps.routing.VerticalMethod
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.min
import com.campusmaps.data.Edge as CoreEdge
import com.campusmaps.data.EdgeKind as CoreEdgeKind
import com.campusmaps.routing.RouteOption as CoreOption

// Routing adapter: every route in the app comes from Raphael's core Router.route(...).
//
// In: the app's Building view (data/campus/CoreBridge.kt), a start node id (an OUTDOOR node means core
// Start.Outside(lat, lng); anything else Start.AtNode(id, cameFrom)), the destination, `now` from AppClock
// (real or simulated; core never reads the clock), avoid stairs, and approved student shortcuts.
// Out: the teammate's RoutePlan / RouteOption / Route / RouteStep, so S1b, S2, S3, GuidanceEngine, the watch
// and the glasses keep working unchanged. Instruction text is core's, word for word.
class CoreRouter {

    fun plan(
        building: Building,
        startId: String,
        destinationId: String,
        time: LocalDateTime,
        avoidStairs: Boolean,
        extraEdges: List<GraphEdge> = emptyList(),
        cameFromId: String? = null,
        hasCard: Boolean = false,
    ): RoutePlan {
        val start = building.node(startId)
        val destination = building.node(destinationId)

        if (startId == destinationId) {
            // docs/20 QA #14: "You are already here", never a 0:00 card.
            val point = RoutePoint(start, EdgeKind.WALK, 0.0)
            val step = RouteStep(StepKind.ALREADY_THERE, "You are at ${destination.name}", 0, 0.0, start.floor, place = destination.name)
            return RoutePlan.AlreadyHere(destination, Route(building.id, listOf(point), listOf(step), destination))
        }

        val core = withShortcuts(building, extraEdges)
        val coreStart = coreStart(building, start, cameFromId)
        val options = Router.route(core, coreStart, destinationId, Prefs(avoidStairs = avoidStairs, now = time, hasCard = hasCard))
        if (options.isEmpty()) {
            val outdoorEntrances = core.nodes.filter { it.isOutdoorEntrance }
            val allLocked = start.isOutdoor && outdoorEntrances.isNotEmpty() && outdoorEntrances.all { Access.blocks(it, time, hasCard) }
            val message = when {
                allLocked -> RouteText.noRouteLocked(destination.name, Formats.dayTime(time), closed = outdoorEntrances.all { Access.isClosed(it, time) })
                avoidStairs && Router.route(core, coreStart, destinationId, Prefs(false, time, hasCard)).isNotEmpty() ->
                    RouteText.noRouteStepFree(destination.name)
                else -> RouteText.noRouteUnknown(destination.name)
            }
            return RoutePlan.NoRoute(message)
        }
        val studentEdges = extraEdges.filter { it.source == EdgeSource.STUDENT }
        val cards = options.mapIndexed { i, o -> toOption(building, core, start, o, i, studentEdges) }
        return RoutePlan.Options(cards, options.first().notice?.let(LockedNotice::fromCore))
    }

    // Reroute helper: the best route from here, preferring the same way of changing floors (Raphael's NavLogic.pickReroute).
    fun bestRoute(
        building: Building,
        startId: String,
        destinationId: String,
        time: LocalDateTime,
        avoidStairs: Boolean,
        extraEdges: List<GraphEdge> = emptyList(),
        cameFromId: String? = null,
        preferMethod: FloorChange? = null,
        hasCard: Boolean = false,
    ): Route? = when (val plan = plan(building, startId, destinationId, time, avoidStairs, extraEdges, cameFromId, hasCard)) {
        is RoutePlan.Options -> (plan.options.firstOrNull { it.method == preferMethod } ?: plan.options.first()).route
        is RoutePlan.AlreadyHere -> plan.route
        is RoutePlan.NoRoute -> null
    }

    // Approved shortcuts become extra core edges. Open issue 1 of WHATS-LEFT: a shortcut is a walk, so one whose ends
    // are on different floors (or not both in the building file) is dropped here, whatever the review said.
    private fun withShortcuts(building: Building, extra: List<GraphEdge>): com.campusmaps.data.Building {
        val core = building.core
        val usable = extra.filter { usableShortcut(building, it) }
        if (usable.isEmpty()) return core
        return core.copy(edges = core.edges + usable.map { e ->
            CoreEdge(e.from, e.to, e.lengthM, CoreEdgeKind.HALLWAY, notes = "student shortcut: ${e.shortcutName}")
        })
    }

    private fun coreStart(building: Building, start: GraphNode, cameFromId: String?): Start =
        if (start.isOutdoor) {
            val p = building.outdoorStarts.getValue(start.id)
            Start.Outside(p.lat, p.lng)
        } else {
            Start.AtNode(start.id, cameFromId?.takeIf { building.core.nodeOrNull(it) != null })
        }

    private fun toOption(
        building: Building,
        core: com.campusmaps.data.Building,
        start: GraphNode,
        o: CoreOption,
        index: Int,
        studentEdges: List<GraphEdge>,
    ): RouteOption {
        val ids = o.nodes.map { if (it == Router.OUTSIDE) start.id else it }
        val points = mutableListOf<RoutePoint>()
        var walked = 0.0
        ids.forEachIndexed { i, id ->
            val node = building.node(id)
            if (i == 0) {
                // Outside, the start point is drawn and tracked on the floor of the entrance this route goes in by
                // (Classroom South has floor-2 entrances), so the outdoor leg is a flat walk, not a "ride".
                val start = if (node.isOutdoor && ids.size > 1) node.copy(floor = building.node(ids[1]).floor) else node
                points += RoutePoint(start, EdgeKind.WALK, 0.0)
                return@forEachIndexed
            }
            val prevId = ids[i - 1]
            val student = studentEdges.firstOrNull { (it.from == prevId && it.to == id) || (it.from == id && it.to == prevId) }
            val edge = findEdge(core, prevId, id)
            val kind = when (edge?.kind) {
                CoreEdgeKind.STAIRS -> EdgeKind.STAIRS
                CoreEdgeKind.ELEVATOR -> EdgeKind.ELEVATOR
                else -> EdgeKind.WALK
            }
            if (kind == EdgeKind.WALK) {
                walked += when {
                    o.nodes[i - 1] == Router.OUTSIDE -> outsideLeg(building, start, id)
                    edge != null -> edge.lengthM
                    student != null -> student.lengthM
                    else -> building.node(prevId).position.distanceTo(node.position)
                }
            }
            points += RoutePoint(node, kind, walked, arrivedByStudentEdge = student != null && edge?.notes?.startsWith("student") == true)
        }
        val destination = building.node(ids.last())
        val cardName = if (o.cardNeeded) com.campusmaps.data.campus.Campuses.of(building.id).cardName else null
        val route = Route(building.id, points, steps(o.instructions, o.nodes, points, cardName), destination, ids)
        val method = when (o.verticalMethod) {
            VerticalMethod.ELEVATOR -> FloorChange.ELEVATOR
            VerticalMethod.STAIRS -> FloorChange.STAIRS
            VerticalMethod.NONE -> FloorChange.LEVEL
        }
        val floors = points.zipWithNext().sumOf { (a, b) -> if (b.arrivedBy != EdgeKind.WALK) abs(b.floor - a.floor) else 0 }
        val shortcut = points.indices.drop(1).firstOrNull { points[it].arrivedByStudentEdge }?.let { i ->
            studentEdges.firstOrNull { e -> setOf(e.from, e.to) == setOf(ids[i - 1], ids[i]) }
        }
        return RouteOption(
            id = "${o.entrance ?: "inside"}-${method.name.lowercase()}-$index",
            entrance = o.entrance?.let { building.nodes[it] },
            method = method,
            etaSeconds = o.etaSec,
            walkM = o.distanceM,
            floorsChanged = floors,
            elevatorWaitS = if (method == FloorChange.ELEVATOR) o.eta.elevatorWaitSec.toInt() else null,
            usesStudentShortcut = shortcut != null,
            shortcutName = shortcut?.shortcutName,
            alsoVia = o.alsoVia,
            route = route,
            core = o,
            cardNeeded = o.cardNeeded,
        )
    }

    // Same distance core's Router uses for OUTSIDE -> entrance.
    private fun outsideLeg(building: Building, start: GraphNode, entranceId: String): Double {
        val p = building.outdoorStarts[start.id]
        val e = building.core.node(entranceId)
        return if (p != null && e.lat != null && e.lng != null) Geo.haversineM(p.lat, p.lng, e.lat!!, e.lng!!)
        else start.position.distanceTo(building.node(entranceId).position)
    }

    // The edge core walked between a and b (honouring one-way edges); the shortest if there are several.
    private fun findEdge(core: com.campusmaps.data.Building, a: String, b: String): CoreEdge? =
        core.edges.filter { (it.from == a && it.to == b) || (!it.oneWay && it.from == b && it.to == a) }.minByOrNull { it.lengthM }

    // Core instructions -> RouteSteps. The text is core's; this only adds where each step starts and when it is done,
    // which is what GuidanceEngine needs to follow a pose along the route.
    private fun steps(instructions: List<Instruction>, coreNodes: List<String>, points: List<RoutePoint>, cardName: String? = null): List<RouteStep> {
        val out = mutableListOf<RouteStep>()
        val last = points.lastIndex
        val list = instructions.filter { it.type != InstructionType.LOCKED_NOTICE }
        var cursor = 0
        var k = 0
        fun indexOf(atNode: String): Int {
            val i = (cursor..last).firstOrNull { coreNodes[it] == atNode } ?: coreNodes.indexOf(atNode).coerceAtLeast(0)
            cursor = i
            return i
        }
        fun cum(i: Int) = points[i.coerceIn(0, last)].cumulativeM
        fun floor(i: Int) = points[i.coerceIn(0, last)].floor
        fun name(i: Int) = points[i.coerceIn(0, last)].node.name
        while (k < list.size) {
            val ins = list[k]
            val i = indexOf(ins.atNode)
            when {
                ins.type == InstructionType.ARRIVE && last == 0 ->
                    out += RouteStep(StepKind.ALREADY_THERE, ins.text, 0, 0.0, floor(0), place = name(0))
                ins.type == InstructionType.ARRIVE ->
                    out += RouteStep(StepKind.ARRIVE, ins.text, last, cum(last), floor(last), place = name(last), side = sideOf(ins.text))
                ins.type == InstructionType.START && coreNodes[0] == Router.OUTSIDE && i == 0 -> {
                    // "Walk to <entrance>", then near the door the banner switches to core's "Go through <entrance>".
                    val next = list.getOrNull(k + 1)
                    val approach = next?.takeIf { it.type == InstructionType.ENTRANCE }?.text
                    if (approach != null) k++
                    // Card holder at a card-only door: near it, say "Tap your PantherCard at the Main entrance" instead.
                    out += RouteStep(StepKind.WALK_TO_ENTRANCE, ins.text, 1.coerceAtMost(last), cum(1), floor(1), place = name(1),
                        approachText = cardName?.let { "Tap your $it at the ${name(1)}" } ?: approach, cardName = cardName)
                    cursor = 1.coerceAtMost(last)
                }
                i == 0 && (ins.type == InstructionType.START || ins.type == InstructionType.TURN) -> {
                    val kind = when {
                        ins.type == InstructionType.TURN -> turnKind(ins.direction)
                        ins.text.startsWith("Continue") -> StepKind.CONTINUE
                        else -> StepKind.HEAD
                    }
                    out += RouteStep(kind, ins.text, 0, min(cum(1), SHORT_STEP_M), floor(0), place = name(1))
                }
                ins.type == InstructionType.ELEVATOR || ins.type == InstructionType.STAIRS_UP || ins.type == InstructionType.STAIRS_DOWN -> {
                    var end = i
                    while (end + 1 <= last && points[end + 1].arrivedBy != EdgeKind.WALK) end++
                    out += RouteStep(
                        kind = if (ins.type == InstructionType.ELEVATOR) StepKind.ELEVATOR else StepKind.STAIRS,
                        text = ins.text,
                        startIndex = i,
                        completeAtM = cum(end),
                        completeFloor = floor(end),
                        place = name(i),
                        targetFloor = floor(end),
                        fromFloor = floor(i),
                    )
                }
                ins.type == InstructionType.TURN && i > 0 && points[i].arrivedBy != EdgeKind.WALK ->
                    // Core's "Exit toward X" right after a ride.
                    // Done after half the first leg (at most 4 m), so a turn right after the exit still gets its own moment.
                    out += RouteStep(StepKind.EXIT_TOWARD, ins.text, i, cum(i) + min(SHORT_STEP_M, (cum(i + 1) - cum(i)) / 2), floor(i), place = name(i + 1))
                ins.type == InstructionType.TURN ->
                    out += RouteStep(turnKind(ins.direction), ins.text, i, cum(i), floor(i), place = name(i))
                else -> // DOOR, or an ENTRANCE reached through an outdoor edge in the file
                    out += RouteStep(StepKind.DOOR, ins.text, i, cum(i), floor(i), place = name(i))
            }
            k++
        }
        if (out.isEmpty()) out += RouteStep(StepKind.ARRIVE, "You have arrived at ${name(last)}", last, cum(last), floor(last), place = name(last))
        return out
    }

    private fun turnKind(d: Direction): StepKind = when (d) {
        Direction.LEFT -> StepKind.TURN_LEFT
        Direction.RIGHT -> StepKind.TURN_RIGHT
        Direction.U_TURN -> StepKind.TURN_AROUND
        else -> StepKind.CONTINUE
    }

    private fun sideOf(text: String): Side? = when {
        text.endsWith("on your left") -> Side.LEFT
        text.endsWith("on your right") -> Side.RIGHT
        text.endsWith("is ahead") -> Side.AHEAD
        text.endsWith("is behind") -> Side.BEHIND
        else -> null
    }

    companion object {
        // HEAD and EXIT steps finish after this many metres, so the next turn can show its distance (teammate's rule).
        private const val SHORT_STEP_M = 4.0

        // The option that goes in by [entranceId] (the entrance the Explore map drew), directly or as one of its "also via"
        // entrances (core lists those by name); the first option when none does.
        fun optionForEntrance(building: Building, options: List<RouteOption>, entranceId: String): RouteOption {
            val name = building.nodes[entranceId]?.name
            return options.firstOrNull { it.entrance?.id == entranceId }
                ?: options.firstOrNull { name != null && name in it.alsoVia }
                ?: options.first()
        }

        // A shortcut is usable when both ends are in the building file and on the same floor.
        fun usableShortcut(building: Building, e: GraphEdge): Boolean {
            val a = building.core.nodeOrNull(e.from) ?: return false
            val b = building.core.nodeOrNull(e.to) ?: return false
            return e.kind == EdgeKind.WALK && a.floor == b.floor
        }
    }
}
