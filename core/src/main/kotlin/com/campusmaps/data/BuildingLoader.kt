package com.campusmaps.data

import kotlinx.serialization.json.Json
import kotlin.math.hypot

object BuildingLoader {
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false; explicitNulls = false }

    fun fromJson(text: String): Building = json.decodeFromString(Building.serializer(), text)
    fun toJson(b: Building): String = json.encodeToString(Building.serializer(), b)
}

/** ERROR breaks routing or a demo (rules 0 to 8); WARN is suspicious data (rule 9); INFO is worth a log line (rule 10). */
enum class Severity { ERROR, WARN, INFO }

data class Problem(val rule: Int, val message: String, val severity: Severity = Severity.ERROR) {
    override fun toString() = if (severity == Severity.ERROR) "rule $rule: $message" else "rule $rule (${severity.name.lowercase()}): $message"
}

/**
 * Rules 1 to 6 and 8 of docs/02 (ERROR). Rule 7 is only the file-exists part, through [imageExists].
 * Extensions: rule 9 (WARN) two nodes closer than [MIN_NODE_GAP_M] on one floor; rule 10 (INFO) an entrance without access windows
 * (treated as always public). Callers that fail fast should look at ERROR only.
 */
object BuildingValidator {
    const val MAX_ANCHOR_GAP_M = 20.0
    const val MIN_NODE_GAP_M = 0.5

    fun validate(b: Building, imageExists: (String) -> Boolean = { true }, demoRoutes: List<List<String>> = emptyList()): List<Problem> {
        val p = mutableListOf<Problem>()
        val ids = b.nodes.map { it.id }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { p += Problem(0, "duplicate node id $it") }
        val known = ids.toSet()
        for (e in b.edges) for (end in listOf(e.from, e.to))
            if (end !in known) p += Problem(1, "edge ${e.from}->${e.to} references missing node $end")
        for (a in b.anchors) if (a.node !in known) p += Problem(2, "anchor ${a.id} references missing node ${a.node}")
        for (n in b.nodes.filter { it.type == NodeType.ELEVATOR })
            if (b.elevator(n.elevatorId) == null) p += Problem(3, "elevator node ${n.id} has unknown elevatorId ${n.elevatorId}")
        for (e in b.edges.filter { it.kind.vertical }) {
            val f = b.nodeOrNull(e.from) ?: continue; val t = b.nodeOrNull(e.to) ?: continue
            val wantType = if (e.kind == EdgeKind.STAIRS) NodeType.STAIRS else NodeType.ELEVATOR
            if (f.type != wantType || t.type != wantType || f.family != t.family || f.floor == t.floor)
                p += Problem(4, "vertical edge ${e.from}->${e.to} must join two ${e.kind.name.lowercase()} nodes of one family on different floors")
            else if (e.floors != 0 && e.floors != kotlin.math.abs(f.floor - t.floor))
                p += Problem(4, "vertical edge ${e.from}->${e.to} says floors=${e.floors} but spans ${kotlin.math.abs(f.floor - t.floor)}")
        }
        if (p.none { it.rule == 1 }) {
            val g = b.graph()
            for (entrance in b.nodes.filter { it.type == NodeType.ENTRANCE }) {
                val seen = mutableSetOf(entrance.id); val queue = ArrayDeque(listOf(entrance.id))
                while (queue.isNotEmpty()) g[queue.removeFirst()].orEmpty().forEach { if (seen.add(it.to)) queue += it.to }
                val missing = known - seen
                if (missing.isNotEmpty()) p += Problem(5, "from entrance ${entrance.id} cannot reach ${missing.sorted()}")
            }
            if (b.nodes.none { it.type == NodeType.ENTRANCE }) p += Problem(5, "building has no entrance")
        }
        for (d in b.demoDestinations) {
            val room = b.nodeOrNull(d) ?: run { p += Problem(6, "demo destination $d is not a node"); continue }
            if (b.anchors.none { it.floor == room.floor && hypot(it.x - room.x, it.y - room.y) <= MAX_ANCHOR_GAP_M })
                p += Problem(6, "demo destination $d has no anchor within 20 m on floor ${room.floor}")
        }
        for (a in b.anchors.filter { it.kind == AnchorKind.IMAGE }) {
            if (a.widthM == null) p += Problem(7, "image anchor ${a.id} has no widthM")
            if (a.image == null || !imageExists(a.image)) p += Problem(7, "image anchor ${a.id} file missing: ${a.image}")
        }
        demoRoutes.forEach { p += checkAnchorSpacing(b, it) }
        for (floor in b.nodes.groupBy { it.floor }.values) for (i in floor.indices) for (j in i + 1 until floor.size) {
            val a = floor[i]; val c = floor[j]
            if (a.id != c.id && hypot(a.x - c.x, a.y - c.y) < MIN_NODE_GAP_M)
                p += Problem(9, "nodes ${a.id} and ${c.id} are %.2f m apart on floor ${a.floor}".format(hypot(a.x - c.x, a.y - c.y)), Severity.WARN)
        }
        for (e in b.nodes.filter { it.type == NodeType.ENTRANCE && it.access.isNullOrEmpty() })
            p += Problem(10, "entrance ${e.id} has no access windows (treated as always public)", Severity.INFO)
        return p
    }

    /** Rule 8: along [route] (node ids), no stretch longer than 20 m without a node that has an anchor. Start and end count as stretch ends. */
    fun checkAnchorSpacing(b: Building, route: List<String>): List<Problem> {
        val anchored = b.anchors.map { it.node }.toSet()
        var since = 0.0; var lastMark = route.first(); val out = mutableListOf<Problem>()
        for ((i, id) in route.withIndex()) {
            if (i > 0) { val a = b.node(route[i - 1]); val c = b.node(id); if (a.floor == c.floor) since += hypot(a.x - c.x, a.y - c.y) }
            if (id in anchored || i == route.lastIndex) {
                if (since > MAX_ANCHOR_GAP_M) out += Problem(8, "route ${route.first()}->${route.last()}: %.1f m without an anchor between $lastMark and $id".format(since))
                since = 0.0; lastMark = id
            }
        }
        return out
    }
}
