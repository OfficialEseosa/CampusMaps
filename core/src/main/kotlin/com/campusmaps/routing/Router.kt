package com.campusmaps.routing

import com.campusmaps.data.*
import java.time.LocalDateTime
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil

sealed interface Start {
    /**
     * Already inside at node [id]. [cameFrom] is the node walked from just before (for a reroute): when it is on the same floor,
     * cameFrom -> id is the incoming direction, so the first instruction can say left or right instead of "Head toward".
     */
    data class AtNode(val id: String, val cameFrom: String? = null) : Start
    /** Outdoors near (lat, lng): a virtual [Router.OUTSIDE] node joined to every outdoor entrance by straight-line distance. */
    data class Outside(val lat: Double, val lng: Double) : Start
}

/** [now] is always supplied by the caller (real clock or the debug overlay); routing never reads the clock. */
data class Prefs(val avoidStairs: Boolean = false, val now: LocalDateTime)

enum class VerticalMethod { NONE, STAIRS, ELEVATOR }

data class Eta(val walkSec: Double = 0.0, val stairsSec: Double = 0.0, val elevatorWaitSec: Double = 0.0, val elevatorRideSec: Double = 0.0) {
    val totalSec: Double get() = walkSec + stairsSec + elevatorWaitSec + elevatorRideSec
    operator fun plus(o: Eta) = Eta(walkSec + o.walkSec, stairsSec + o.stairsSec, elevatorWaitSec + o.elevatorWaitSec, elevatorRideSec + o.elevatorRideSec)
}

data class RouteOption(
    val nodes: List<String>,
    val eta: Eta,
    val distanceM: Double,
    val entrance: String?,
    val entranceName: String?,
    val entranceFloor: Int?,
    val verticalMethod: VerticalMethod,
    val instructions: List<Instruction>,
    val notice: String?,
    /** Other entrances (names) that give the same route within [Router.DUPLICATE_WITHIN_SEC]; collapsed into this card. */
    val alsoVia: List<String> = emptyList(),
) {
    val etaSec: Double get() = eta.totalSec
    val etaMinutes: Int get() = ceil(etaSec / 60).toInt().coerceAtLeast(1)

    /** Card label, e.g. "Walters main, floor 1, elevator, 3 min". */
    fun label(): String = listOfNotNull(entranceName, entranceFloor?.let { "floor $it" },
        verticalMethod.takeIf { it != VerticalMethod.NONE }?.name?.lowercase(), "$etaMinutes min").joinToString(", ")

    fun describe(): String = buildString {
        appendLine("${label()}  [%.0f s: walk %.0f, stairs %.0f, wait %.0f, ride %.0f; %.0f m]".format(
            etaSec, eta.walkSec, eta.stairsSec, eta.elevatorWaitSec, eta.elevatorRideSec, distanceM))
        if (alsoVia.isNotEmpty()) appendLine("  also via: ${alsoVia.joinToString()}")
        appendLine("  ${nodes.joinToString(" > ")}")
        notice?.let { appendLine("  NOTICE: $it") }
        instructions.forEachIndexed { i, s -> appendLine("  ${i + 1}. [${s.type}/${s.direction}] ${s.text}  (%.0f m)".format(s.distanceM)) }
    }
}

object Router {
    const val OUTSIDE = "OUTSIDE"
    const val TURN_PENALTY_SEC = 2.0
    const val DOOR_PENALTY_SEC = 2.0
    const val DUPLICATE_WITHIN_SEC = 15.0
    /** Two options whose walking distance differs by less than this (and ETA within [DUPLICATE_WITHIN_SEC]) are one card. */
    const val SAME_DISTANCE_M = 5.0
    /** Cards closer than this in ETA are not visibly different; the slower one is dropped (see [route]). */
    const val MIN_CARD_GAP_SEC = 20.0
    const val MAX_OPTIONS = 3

    /**
     * Best-first options, at most 3, each meaningfully different (docs/04): a different vertical method, or a different entrance whose
     * route is not the same one in disguise. An option is folded into a better one with the same method when its ETA is within
     * [DUPLICATE_WITHIN_SEC] and any of: same entrance; same nodes after the entrance (vestibule waypoints skipped); walking distance
     * within [SAME_DISTANCE_M]; or a card label that would differ only by the entrance name. A folded option from another entrance is
     * listed in the survivor's [RouteOption.alsoVia]. Then no two cards may be within [MIN_CARD_GAP_SEC]: the slower one goes, except
     * that the fastest option of each vertical method is always kept (it evicts unprotected cards near it instead), so an elevator
     * and a stairs card both survive when they exist, even if they are closer than the gap. Empty if the destination is unreachable.
     */
    fun route(b: Building, start: Start, destination: String, prefs: Prefs): List<RouteOption> {
        b.node(destination)
        val net = Net(b, start)
        val entrances: List<String?> = if (start is Start.Outside)
            b.nodes.filter { it.isOutdoorEntrance && !Access.isLocked(it, prefs.now) }.map { it.id } else listOf(null)
        val found = entrances.flatMap { e -> listOf(VerticalMethod.STAIRS, VerticalMethod.ELEVATOR).mapNotNull { v ->
            net.dijkstra(destination, prefs, entrance = e, method = v, ignoreAccess = false) } }
        val unrestricted = net.dijkstra(destination, prefs, entrance = null, method = null, ignoreAccess = true)

        val options = mutableListOf<Path>()
        val alsoVia = HashMap<Path, MutableList<String>>()
        for (p in found.sortedBy { it.eta.totalSec }) {
            val into = options.firstOrNull { sameCard(net, it, p) }
            if (into == null) { options += p; continue }
            val e = net.entranceOf(p)
            if (e != null && e != net.entranceOf(into)) b.node(e).name.let { n -> alsoVia.getOrPut(into) { mutableListOf() }.let { if (n !in it) it += n } }
        }
        val protectedPaths = options.groupBy { it.method }.values.map { it.first() }.toSet()
        val cards = mutableListOf<Path>()
        for (p in options) {
            val near = cards.filter { kotlin.math.abs(it.eta.totalSec - p.eta.totalSec) < MIN_CARD_GAP_SEC }
            when {
                near.isEmpty() -> cards += p
                p in protectedPaths -> { cards.removeAll(near.filter { it !in protectedPaths }.toSet()); cards += p }
            }
        }
        options.retainAll(cards.toSet())
        val best = options.firstOrNull()
        val notice = unrestricted?.let { net.entranceOf(it) }?.let { b.node(it) }
            ?.takeIf { Access.isLocked(it, prefs.now) && it.id != best?.let(net::entranceOf) }
            ?.let { locked ->
                val instead = best?.let(net::entranceOf)?.let { b.node(it).name }
                "Heads up: ${locked.name} is card-only now. " + (instead?.let { "Using $it instead." } ?: "Taking another way.")
            }
        return options.take(MAX_OPTIONS).map { p ->
            val ent = net.entranceOf(p)?.let { b.node(it) }
            RouteOption(p.nodes, p.eta, p.distanceM, ent?.id, ent?.name, ent?.floor, p.method,
                Instructions.build(net, p, notice.takeIf { p === best }), notice, alsoVia[p].orEmpty())
        }
    }

    /** True when [p] (found after [kept], so no faster) would be a duplicate card of [kept]. */
    private fun sameCard(net: Net, kept: Path, p: Path): Boolean {
        if (kept.nodes == p.nodes) return true
        if (kept.method != p.method || p.eta.totalSec - kept.eta.totalSec > DUPLICATE_WITHIN_SEC) return false
        val ek = net.entranceOf(kept); val ep = net.entranceOf(p)
        if (ek == ep) return true
        if (net.afterEntrance(kept) == net.afterEntrance(p)) return true
        if (kotlin.math.abs(kept.distanceM - p.distanceM) < SAME_DISTANCE_M) return true
        val fk = ek?.let { net.b.node(it).floor }; val fp = ep?.let { net.b.node(it).floor }
        return fk == fp && minutes(kept.eta.totalSec) == minutes(p.eta.totalSec)
    }

    private fun minutes(sec: Double) = ceil(sec / 60).toInt().coerceAtLeast(1)
}

/** A found path: nodes, the edges between them, and its ETA without tie-break penalties. */
internal class Path(val nodes: List<String>, val edges: List<Edge>, val eta: Eta, val distanceM: Double) {
    val method: VerticalMethod get() = when {
        edges.any { it.kind == EdgeKind.ELEVATOR } -> VerticalMethod.ELEVATOR
        edges.any { it.kind == EdgeKind.STAIRS } -> VerticalMethod.STAIRS
        else -> VerticalMethod.NONE
    }
}

/** The building graph plus the virtual OUTSIDE node for this start. */
internal class Net(val b: Building, val start: Start) {
    val startId = when (start) { is Start.AtNode -> start.id.also { b.node(it) }; is Start.Outside -> Router.OUTSIDE }
    private val outside: Node? = (start as? Start.Outside)?.let {
        val (x, y) = Geo.toBuilding(b.origin, it.lat, it.lng)
        Node(Router.OUTSIDE, NodeType.WAYPOINT, "your position", Int.MIN_VALUE, x, y, lat = it.lat, lng = it.lng)
    }
    private val adj: Map<String, List<Adjacent>> = b.graph().toMutableMap().also { g ->
        if (outside != null) g[Router.OUTSIDE] = b.nodes.filter { it.isOutdoorEntrance }.map { e ->
            val d = if (e.lat != null && e.lng != null) Geo.haversineM(outside.lat!!, outside.lng!!, e.lat, e.lng)
                    else kotlin.math.hypot(e.x - outside.x, e.y - outside.y)
            Adjacent(e.id, Edge(Router.OUTSIDE, e.id, d, EdgeKind.OUTDOOR, oneWay = true))
        }
    }

    fun node(id: String): Node = if (id == Router.OUTSIDE) outside!! else b.node(id)

    /** The previous node for a reroute, only if it exists, differs from the start and is on the start's floor. */
    val cameFrom: Node? = (start as? Start.AtNode)?.cameFrom?.let(b::nodeOrNull)
        ?.takeIf { it.id != startId && it.floor == b.node(startId).floor && (it.x != b.node(startId).x || it.y != b.node(startId).y) }
    /** Synthetic incoming edge for [cameFrom], so the first step gets a turn penalty and a left/right instruction. */
    val cameFromEdge: Edge? = cameFrom?.let { Edge(it.id, startId, kotlin.math.hypot(it.x - node(startId).x, it.y - node(startId).y)) }

    /**
     * The entrance a path goes in through: the node reached by an outdoor edge (OUTSIDE -> entrance for [Start.Outside], or an
     * outdoor edge in the file that re-enters the building). Null for a path that stays inside, even if it passes an entrance node.
     */
    fun entranceOf(p: Path): String? = p.edges.indexOfFirst { it.kind == EdgeKind.OUTDOOR }.takeIf { it >= 0 }
        ?.let { p.nodes[it + 1] }?.takeIf { it != Router.OUTSIDE && b.node(it).type == NodeType.ENTRANCE }

    /** Nodes after the entrance, skipping the waypoints (vestibules) right behind it: two doors into one lobby compare equal. */
    fun afterEntrance(p: Path): List<String> {
        val e = entranceOf(p) ?: return p.nodes
        return p.nodes.drop(p.nodes.indexOf(e) + 1).dropWhile { b.node(it).type == NodeType.WAYPOINT }
    }

    /** Cost of [e] from [from] to [to]; [prev] is the node before [from] (null at the start). Split so the ETA and the penalties stay separate. */
    fun cost(prev: String?, from: String, to: String, e: Edge, prevEdge: Edge?): Pair<Eta, Double> {
        val f = node(from); val t = node(to)
        val floors = if (e.floors != 0) e.floors else abs(f.floor - t.floor)
        val eta = when (e.kind) {
            EdgeKind.STAIRS -> Eta(stairsSec = floors * if (t.floor > f.floor) b.stairsSecondsPerFloor else b.stairsDownSecondsPerFloor)
            EdgeKind.ELEVATOR -> {
                val spec = b.elevator(f.elevatorId) ?: ElevatorSpec("default")
                Eta(elevatorWaitSec = if (prevEdge?.kind == EdgeKind.ELEVATOR) 0.0 else spec.avgWaitSec, elevatorRideSec = floors * spec.secondsPerFloor)
            }
            else -> Eta(walkSec = e.lengthM / b.walkingSpeedMps)
        }
        var penalty = if (e.kind == EdgeKind.DOOR) Router.DOOR_PENALTY_SEC else 0.0
        if (prev != null && prevEdge?.kind?.vertical == false && !e.kind.vertical &&
            Instructions.turn(node(prev), f, t) !in setOf(Direction.STRAIGHT, null)) penalty += Router.TURN_PENALTY_SEC
        return eta to penalty
    }

    private data class State(val node: String, val prev: String?, val edge: Edge?)

    /** Dijkstra over (node, previous node) states so turn penalties and single elevator waits are exact. */
    fun dijkstra(dest: String, prefs: Prefs, entrance: String?, method: VerticalMethod?, ignoreAccess: Boolean): Path? {
        fun allowed(to: String, e: Edge): Boolean {
            if (e.kind == EdgeKind.STAIRS && (prefs.avoidStairs || method == VerticalMethod.ELEVATOR)) return false
            if (e.kind == EdgeKind.ELEVATOR && method == VerticalMethod.STAIRS) return false
            if (e.from == Router.OUTSIDE && entrance != null && to != entrance) return false
            if (!ignoreAccess && to != startId && Access.isLocked(b.node(to), prefs.now)) return false
            return true
        }
        val first = State(startId, null, null)
        // The reroute direction only shapes the first turn; it never forbids walking back the way the user came.
        fun prevOf(s: State) = if (s == first) cameFrom?.id else s.prev
        fun prevEdgeOf(s: State) = if (s == first) cameFromEdge else s.edge
        val best = HashMap<State, Double>().apply { put(first, 0.0) }
        val parent = HashMap<State, State>()
        val queue = PriorityQueue<Pair<Double, State>>(compareBy { it.first }).apply { add(0.0 to first) }
        while (queue.isNotEmpty()) {
            val (d, s) = queue.poll()
            if (d > best.getValue(s)) continue
            if (s.node == dest) {
                val states = generateSequence(s) { parent[it] }.toList().reversed()
                val edges = states.drop(1).map { it.edge!! }
                var eta = Eta()
                states.zipWithNext().forEach { (a, c) -> eta += cost(prevOf(a), a.node, c.node, c.edge!!, prevEdgeOf(a)).first }
                return Path(states.map { it.node }, edges, eta, edges.filter { !it.kind.vertical }.sumOf { it.lengthM })
            }
            for (a in adj[s.node].orEmpty()) {
                if (a.to == s.prev || !allowed(a.to, a.edge)) continue
                val (eta, pen) = cost(prevOf(s), s.node, a.to, a.edge, prevEdgeOf(s))
                val next = State(a.to, s.node, a.edge); val nd = d + eta.totalSec + pen
                if (nd < (best[next] ?: Double.MAX_VALUE)) { best[next] = nd; parent[next] = s; queue.add(nd to next) }
            }
        }
        return null
    }
}
