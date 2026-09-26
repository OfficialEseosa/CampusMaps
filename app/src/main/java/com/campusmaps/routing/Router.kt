package com.campusmaps.routing

import com.campusmaps.data.model.Building
import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.EdgeSource
import com.campusmaps.data.model.GraphEdge
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.NodeKind
import java.time.LocalDateTime
import java.util.PriorityQueue
import kotlin.math.abs

// Numbers the router uses to turn metres and floors into seconds.
data class RoutingConfig(
    val walkSpeedMps: Double = 1.3,          // Average indoor walking speed
    val elevatorWaitS: Double = 35.0,        // Average wait for the elevator to arrive
    val elevatorPerFloorS: Double = 6.0,     // Ride time per floor
    val stairsUpPerFloorS: Double = 22.0,    // Climbing one floor
    val stairsDownPerFloorS: Double = 16.0,  // Going down one floor
    val foldWithinS: Double = 15.0,          // Entrances this close to a better one become "also via"
    val maxOptions: Int = 3,                 // S1b shows 1 to 3 cards
)

// Finds routes through a building graph and packages them as S1b cards.
//
// How it works, in plain words:
// 1. For every open entrance (or "from here" when starting inside) and every way of changing
//    floor (elevator or stairs), run Dijkstra's shortest path, measuring cost in seconds.
// 2. Keep the fastest route for each entrance + method pair.
// 3. If two entrances give nearly the same route, fold the slower one into "also via".
// 4. Best first, at most three cards.
class Router(private val config: RoutingConfig = RoutingConfig()) {

    fun plan(
        building: Building,
        startId: String,
        destinationId: String,
        time: LocalDateTime,
        avoidStairs: Boolean,
        extraEdges: List<GraphEdge> = emptyList(),
        startHeadingRad: Double? = null,
    ): RoutePlan {
        val start = building.node(startId)
        val destination = building.node(destinationId)

        if (startId == destinationId) {
            val points = listOf(RoutePoint(start, EdgeKind.WALK, 0.0))
            return RoutePlan.AlreadyHere(destination, Route(building.id, points, StepWriter.write(points, destination), destination))
        }

        val graph = Graph(building, extraEdges)
        val options = buildOptions(graph, start, destination, time, avoidStairs, allowLocked = false, startHeadingRad)

        if (options.isEmpty()) {
            val allLocked = start.isOutdoor && building.entrances.all { building.isCardOnly(it.id, time) }
            val message = when {
                allLocked -> Instructions.noRouteLocked(destination.name, Formats.dayTime(time))
                avoidStairs && buildOptions(graph, start, destination, time, false, false, startHeadingRad).isNotEmpty() ->
                    Instructions.noRouteStepFree(destination.name)
                else -> Instructions.noRouteUnknown(destination.name)
            }
            return RoutePlan.NoRoute(message)
        }

        // Would a locked door have been the best way in? Then say so, calmly.
        var notice: LockedNotice? = null
        if (start.isOutdoor) {
            val natural = buildOptions(graph, start, destination, time, avoidStairs, allowLocked = true, startHeadingRad).firstOrNull()
            val naturalEntrance = natural?.entrance
            if (naturalEntrance != null &&
                building.isCardOnly(naturalEntrance.id, time) &&
                naturalEntrance.id != options.first().entrance?.id
            ) {
                notice = LockedNotice(naturalEntrance.name, options.first().entrance?.name)
            }
        }
        return RoutePlan.Options(options, notice)
    }

    // Shortcut used for rerouting: the single best route, or null.
    fun bestRoute(
        building: Building,
        startId: String,
        destinationId: String,
        time: LocalDateTime,
        avoidStairs: Boolean,
        extraEdges: List<GraphEdge> = emptyList(),
        startHeadingRad: Double? = null,
    ): Route? = when (val plan = plan(building, startId, destinationId, time, avoidStairs, extraEdges, startHeadingRad)) {
        is RoutePlan.Options -> plan.options.first().route
        is RoutePlan.AlreadyHere -> plan.route
        is RoutePlan.NoRoute -> null
    }

    private fun buildOptions(
        graph: Graph,
        start: GraphNode,
        destination: GraphNode,
        time: LocalDateTime,
        avoidStairs: Boolean,
        allowLocked: Boolean,
        startHeadingRad: Double?,
    ): List<RouteOption> {
        val building = graph.building
        // Outside: try each entrance on its own. Inside: one "from here" search.
        val entrances: List<GraphNode?> = if (start.isOutdoor) {
            building.entrances.filter { allowLocked || !building.isCardOnly(it.id, time) }
        } else {
            listOf(null)
        }
        val methods = if (avoidStairs) listOf(FloorChange.ELEVATOR) else listOf(FloorChange.ELEVATOR, FloorChange.STAIRS)

        // Best option per (entrance, method actually used).
        val best = linkedMapOf<String, RouteOption>()
        for (entrance in entrances) {
            for (method in methods) {
                val found = search(graph, start, destination, entrance, method, startHeadingRad) ?: continue
                if (avoidStairs && found.method == FloorChange.STAIRS) continue
                val key = "${entrance?.id ?: "inside"}-${found.method}"
                val current = best[key]
                if (current == null || found.etaSeconds < current.etaSeconds) best[key] = found
            }
        }

        // Fold near duplicates (same method, different entrance, only a few seconds slower).
        val kept = mutableListOf<RouteOption>()
        for (option in best.values.sortedBy { it.etaSeconds }) {
            val index = kept.indexOfFirst { k ->
                k.method == option.method &&
                    k.entrance?.id != option.entrance?.id &&
                    !k.usesStudentShortcut && !option.usesStudentShortcut &&
                    option.etaSeconds - k.etaSeconds <= config.foldWithinS
            }
            if (index >= 0 && option.entrance != null) {
                kept[index] = kept[index].copy(alsoVia = kept[index].alsoVia + option.entrance.name)
            } else {
                kept += option
            }
        }
        return kept.take(config.maxOptions)
    }

    // Dijkstra over (node, "are we already inside an elevator?") so the elevator wait
    // is paid once per ride, not once per floor.
    private fun search(
        graph: Graph,
        start: GraphNode,
        destination: GraphNode,
        onlyEntrance: GraphNode?,
        method: FloorChange,
        startHeadingRad: Double?,
    ): RouteOption? {
        data class State(val nodeId: String, val inElevator: Boolean)
        data class Entry(val cost: Double, val state: State)

        fun nodeAllowed(node: GraphNode): Boolean = when {
            node.id == start.id || node.id == destination.id -> true
            // Starting outside: only go in through the entrance we are testing.
            start.isOutdoor && node.kind == NodeKind.ENTRANCE -> node.id == onlyEntrance?.id
            // Starting inside: never leave the building.
            !start.isOutdoor && (node.kind == NodeKind.ENTRANCE || node.isOutdoor) -> false
            // Only walk through room doors that are on the corridor line, never use rooms as a way out.
            else -> true
        }

        fun edgeAllowed(edge: GraphEdge): Boolean = when (edge.kind) {
            EdgeKind.WALK -> true
            EdgeKind.ELEVATOR -> method == FloorChange.ELEVATOR
            EdgeKind.STAIRS -> method == FloorChange.STAIRS
        }

        val startState = State(start.id, false)
        val best = hashMapOf(startState to 0.0)
        val previous = hashMapOf<State, Pair<State, GraphEdge>>()
        val queue = PriorityQueue<Entry>(compareBy { it.cost })
        queue += Entry(0.0, startState)
        var goal: State? = null

        while (queue.isNotEmpty()) {
            val (cost, state) = queue.poll()!!
            if (cost > (best[state] ?: Double.MAX_VALUE)) continue
            if (state.nodeId == destination.id) {
                goal = state
                break
            }
            val here = graph.building.node(state.nodeId)
            for (edge in graph.edgesFrom(state.nodeId)) {
                if (!edgeAllowed(edge)) continue
                val otherId = if (edge.from == state.nodeId) edge.to else edge.from
                val other = graph.building.node(otherId)
                if (!nodeAllowed(other)) continue
                val step = edgeCost(edge, here, other, state.inElevator)
                val next = State(otherId, edge.kind == EdgeKind.ELEVATOR)
                val newCost = cost + step
                if (newCost < (best[next] ?: Double.MAX_VALUE)) {
                    best[next] = newCost
                    previous[next] = state to edge
                    queue += Entry(newCost, next)
                }
            }
        }
        val end = goal ?: return null

        // Walk back from the goal to rebuild the path.
        val chain = mutableListOf<Pair<State, GraphEdge?>>()
        var cursor: State? = end
        while (cursor != null) {
            val link = previous[cursor]
            chain += cursor to link?.second
            cursor = link?.first
        }
        chain.reverse()

        var walked = 0.0
        val points = chain.mapIndexed { index, (state, edgeIn) ->
            val node = graph.building.node(state.nodeId)
            if (edgeIn != null && edgeIn.kind == EdgeKind.WALK) walked += edgeIn.lengthM
            RoutePoint(
                node = node,
                arrivedBy = if (index == 0 || edgeIn == null) EdgeKind.WALK else edgeIn.kind,
                cumulativeM = walked,
                arrivedByStudentEdge = edgeIn?.source == EdgeSource.STUDENT,
            )
        }

        val usedEdges = chain.mapNotNull { it.second }
        val usedMethod = when {
            usedEdges.any { it.kind == EdgeKind.ELEVATOR } -> FloorChange.ELEVATOR
            usedEdges.any { it.kind == EdgeKind.STAIRS } -> FloorChange.STAIRS
            else -> FloorChange.LEVEL
        }
        val studentEdge = usedEdges.firstOrNull { it.source == EdgeSource.STUDENT }
        val route = Route(
            buildingId = graph.building.id,
            points = points,
            steps = StepWriter.write(points, destination, startHeadingRad),
            destination = destination,
        )
        return RouteOption(
            id = "${onlyEntrance?.id ?: "inside"}-$usedMethod",
            entrance = onlyEntrance,
            method = usedMethod,
            etaSeconds = best.getValue(end),
            walkM = walked,
            floorsChanged = abs(destination.floor - start.floor),
            elevatorWaitS = if (usedMethod == FloorChange.ELEVATOR) config.elevatorWaitS.toInt() else null,
            usesStudentShortcut = studentEdge != null,
            shortcutName = studentEdge?.shortcutName,
            alsoVia = emptyList(),
            route = route,
        )
    }

    private fun edgeCost(edge: GraphEdge, from: GraphNode, to: GraphNode, inElevator: Boolean): Double = when (edge.kind) {
        EdgeKind.WALK -> edge.lengthM / config.walkSpeedMps
        EdgeKind.ELEVATOR -> config.elevatorPerFloorS + if (inElevator) 0.0 else config.elevatorWaitS
        EdgeKind.STAIRS -> if (to.floor > from.floor) config.stairsUpPerFloorS else config.stairsDownPerFloorS
    }

    // Adjacency list built once per plan, including any approved student shortcuts.
    private class Graph(val building: Building, extraEdges: List<GraphEdge>) {
        private val adjacency: Map<String, List<GraphEdge>>

        init {
            val all = building.edges + extraEdges.filter { e ->
                val a = building.nodes[e.from]
                val b = building.nodes[e.to]
                // A walking edge can't change floors; a cross-floor one would be routed as "same floor".
                a != null && b != null && (e.kind != EdgeKind.WALK || a.floor == b.floor)
            }
            val map = hashMapOf<String, MutableList<GraphEdge>>()
            for (edge in all) {
                map.getOrPut(edge.from) { mutableListOf() } += edge
                map.getOrPut(edge.to) { mutableListOf() } += edge
            }
            adjacency = map
        }

        fun edgesFrom(nodeId: String): List<GraphEdge> = adjacency[nodeId].orEmpty()
    }
}
