package com.campusmaps.data

import kotlinx.serialization.Serializable
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A field fix on top of an asset building file (docs/02 schema). The asset stays the source of truth in git; the phone keeps
 * one patch per building and applies it at load time. Coordinates are core's (metres, +y north).
 *
 * Edges are matched by their two ends in either direction ([EdgeRef]). A node removed here also drops its edges, its anchors
 * and its demo destination entry.
 */
@Serializable
data class BuildingPatch(
    val building: String,
    val addedNodes: List<Node> = emptyList(),
    val changedNodes: List<NodeChange> = emptyList(),
    val removedNodes: List<String> = emptyList(),
    val addedEdges: List<Edge> = emptyList(),
    val removedEdges: List<EdgeRef> = emptyList(),
    val note: String? = null,
) {
    val isEmpty: Boolean
        get() = addedNodes.isEmpty() && changedNodes.isEmpty() && removedNodes.isEmpty() && addedEdges.isEmpty() && removedEdges.isEmpty()

    /** "2 nodes added, 1 changed, 3 edges added" for a status line. */
    fun summary(): String = listOfNotNull(
        "${addedNodes.size} added".takeIf { addedNodes.isNotEmpty() },
        "${changedNodes.size} changed".takeIf { changedNodes.isNotEmpty() },
        "${removedNodes.size} removed".takeIf { removedNodes.isNotEmpty() },
        "${addedEdges.size} links added".takeIf { addedEdges.isNotEmpty() },
        "${removedEdges.size} links removed".takeIf { removedEdges.isNotEmpty() },
    ).joinToString(", ").ifEmpty { "no changes" }

    fun toJson(): String = BuildingLoader.json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(text: String): BuildingPatch = BuildingLoader.json.decodeFromString(serializer(), text)

        /**
         * [base] with [patch] on top. Throws [PatchException] when the patch does not fit the base (wrong building, a removed
         * or changed id that is missing, an added id that already exists, an edge end that does not exist). Order: removed
         * edges, removed nodes, added nodes, changed nodes, added edges.
         */
        fun apply(base: Building, patch: BuildingPatch): Building {
            fun fail(msg: String): Nothing = throw PatchException("${base.code} patch: $msg")
            if (patch.building != base.code) fail("is for building ${patch.building}")
            var edges = base.edges
            for (r in patch.removedEdges) {
                if (edges.none { r.matches(it) }) fail("removed link ${r.a}-${r.b} is not in the file")
                edges = edges.filterNot { r.matches(it) }
            }
            val ids = base.nodes.map { it.id }.toMutableSet()
            for (id in patch.removedNodes) if (!ids.remove(id)) fail("removed node $id is not in the file")
            val removed = patch.removedNodes.toSet()
            var nodes = base.nodes.filter { it.id !in removed }
            edges = edges.filter { it.from !in removed && it.to !in removed }
            for (n in patch.addedNodes) {
                if (!ids.add(n.id)) fail("added node ${n.id} already exists")
            }
            nodes = nodes + patch.addedNodes
            for (c in patch.changedNodes) {
                val i = nodes.indexOfFirst { it.id == c.id }
                if (i < 0) fail("changed node ${c.id} is not in the file")
                nodes = nodes.toMutableList().also { it[i] = c.applyTo(it[i]) }
            }
            for (e in patch.addedEdges) {
                if (e.from !in ids || e.to !in ids) fail("added link ${e.from}-${e.to} has a missing end")
            }
            edges = edges + patch.addedEdges
            return base.copy(
                nodes = nodes,
                edges = edges,
                anchors = base.anchors.filter { it.node !in removed },
                demoDestinations = base.demoDestinations.filter { it !in removed },
            )
        }

        /** [apply], or [base] unchanged plus the reason when the patch does not fit. Never throws. */
        fun applyOrSkip(base: Building, patch: BuildingPatch): Pair<Building, String?> = try {
            apply(base, patch) to null
        } catch (e: Exception) {
            base to (e.message ?: e.toString())
        }

        /** The smallest patch that turns [base] into [edited]. apply(base, diff(base, edited)) has the same nodes and edges as [edited]. */
        fun diff(base: Building, edited: Building, note: String? = null): BuildingPatch {
            val baseById = base.nodes.associateBy { it.id }
            val editedIds = edited.nodes.map { it.id }.toSet()
            val removedNodes = base.nodes.map { it.id }.filter { it !in editedIds }
            val addedNodes = edited.nodes.filter { it.id !in baseById }
            val changedNodes = edited.nodes.mapNotNull { n -> baseById[n.id]?.let { NodeChange.between(it, n) } }
            // Edges grouped by their two ends; a group that differs at all is removed and re-added whole.
            val removedSet = removedNodes.toSet()
            val baseGroups = base.edges.filter { it.from !in removedSet && it.to !in removedSet }.groupBy { EdgeRef.of(it) }
            val editedGroups = edited.edges.groupBy { EdgeRef.of(it) }
            val removedEdges = baseGroups.keys.filter { editedGroups[it] != baseGroups[it] }
            val addedEdges = editedGroups.filter { (k, v) -> baseGroups[k] != v }.values.flatten()
            return BuildingPatch(base.code, addedNodes, changedNodes, removedNodes, addedEdges, removedEdges, note)
        }
    }
}

class PatchException(message: String) : RuntimeException(message)

/** Fields of a node that the editor can change; null means unchanged. [kind] is the node type. */
@Serializable
data class NodeChange(
    val id: String,
    val x: Double? = null,
    val y: Double? = null,
    val name: String? = null,
    val floor: Int? = null,
    val kind: NodeType? = null,
    val doorFacing: String? = null,
) {
    fun applyTo(n: Node): Node = n.copy(
        x = x ?: n.x, y = y ?: n.y, name = name ?: n.name, floor = floor ?: n.floor,
        type = kind ?: n.type, doorFacing = doorFacing ?: n.doorFacing,
    )

    companion object {
        /** Null when nothing the editor can change differs. */
        fun between(old: Node, new: Node): NodeChange? {
            val c = NodeChange(
                id = old.id,
                x = new.x.takeIf { it != old.x },
                y = new.y.takeIf { it != old.y },
                name = new.name.takeIf { it != old.name },
                floor = new.floor.takeIf { it != old.floor },
                kind = new.type.takeIf { it != old.type },
                doorFacing = new.doorFacing.takeIf { it != old.doorFacing },
            )
            return c.takeIf { it != NodeChange(old.id) }
        }
    }
}

/** An edge by its two ends, either direction. Stored with the smaller id first. */
@Serializable
data class EdgeRef(val a: String, val b: String) {
    fun matches(e: Edge): Boolean = (e.from == a && e.to == b) || (e.from == b && e.to == a)

    companion object {
        fun of(e: Edge): EdgeRef = if (e.from <= e.to) EdgeRef(e.from, e.to) else EdgeRef(e.to, e.from)
    }
}

/** What the editor places. Doors are core waypoints named "Door ..." joined with door links. */
enum class EditKind { ROOM, HALLWAY, DOOR }

/**
 * Pure edit steps on a core [Building], used by the phone editor and tested on the JVM.
 * Id rule (CLAUDE.md conventions): rooms R-<number> (the number from the room name, e.g. "Room 612" -> R-612; when taken or
 * absent, the next free R-<n>), hallway points H<n>, doors D<n>, each the next free number above the highest in the file.
 */
object BuildingEdits {
    fun nextFree(b: Building, prefix: String): String {
        val rx = Regex("^" + Regex.escape(prefix) + "(\\d+)$")
        val max = b.nodes.mapNotNull { rx.find(it.id)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "$prefix${max + 1}"
    }

    fun newId(b: Building, kind: EditKind, name: String = ""): String = when (kind) {
        EditKind.HALLWAY -> nextFree(b, "H")
        EditKind.DOOR -> nextFree(b, "D")
        EditKind.ROOM -> {
            val number = Regex("\\d+[A-Za-z]?").find(name)?.value
            val wanted = number?.let { "R-${it.uppercase()}" }
            if (wanted != null && b.nodeOrNull(wanted) == null) wanted else nextFree(b, "R-")
        }
    }

    fun addNode(b: Building, kind: EditKind, name: String, floor: Int, x: Double, y: Double): Pair<Building, String> {
        val id = newId(b, kind, name)
        val cleanName = name.trim().ifEmpty {
            when (kind) {
                EditKind.ROOM -> "Room ${id.removePrefix("R-")}"
                EditKind.HALLWAY -> "Hallway point $id"
                EditKind.DOOR -> "Door $id"
            }
        }
        val node = Node(
            id = id,
            type = if (kind == EditKind.ROOM) NodeType.ROOM else NodeType.WAYPOINT,
            name = cleanName,
            floor = floor,
            x = round2(x),
            y = round2(y),
            estimated = true,
            notes = "Added in the phone editor.",
        )
        return b.copy(nodes = b.nodes + node) to id
    }

    fun isDoor(n: Node): Boolean = n.id.matches(Regex("D\\d+")) || n.name.startsWith("Door", ignoreCase = true)

    /** Straight-line metres between two nodes on one floor, 2 decimals. */
    fun straightLength(a: Node, c: Node): Double = round2(hypot(a.x - c.x, a.y - c.y))

    /**
     * Joins [aId] and [bId]. Same floor: a hallway link (a door link when either end is a door), length the straight line.
     * Different floors: only two stairs or two elevator nodes, a vertical link with floors = the gap and length 0 (the router
     * times it from the building's stairs / elevator settings). Returns null when the link exists or cannot be made.
     */
    fun connect(b: Building, aId: String, bId: String): Building? {
        if (aId == bId) return null
        val a = b.nodeOrNull(aId) ?: return null
        val c = b.nodeOrNull(bId) ?: return null
        if (b.edges.any { EdgeRef(aId, bId).matches(it) }) return null
        val edge = if (a.floor == c.floor) {
            Edge(aId, bId, straightLength(a, c), if (isDoor(a) || isDoor(c)) EdgeKind.DOOR else EdgeKind.HALLWAY,
                estimated = true, notes = "Added in the phone editor (straight line).")
        } else {
            val kind = when {
                a.type == NodeType.STAIRS && c.type == NodeType.STAIRS -> EdgeKind.STAIRS
                a.type == NodeType.ELEVATOR && c.type == NodeType.ELEVATOR -> EdgeKind.ELEVATOR
                else -> return null
            }
            Edge(aId, bId, 0.0, kind, floors = kotlin.math.abs(a.floor - c.floor), estimated = true, notes = "Added in the phone editor.")
        }
        return b.copy(edges = b.edges + edge)
    }

    /** Moves a node. Links added in the editor (estimated straight lines) get their length recomputed; surveyed lengths stay. */
    fun move(b: Building, id: String, x: Double, y: Double): Building {
        val nodes = b.nodes.map { if (it.id == id) it.copy(x = round2(x), y = round2(y)) else it }
        val moved = b.copy(nodes = nodes)
        val edges = b.edges.map { e ->
            if ((e.from == id || e.to == id) && !e.kind.vertical && e.notes?.startsWith("Added in the phone editor") == true) {
                val f = moved.node(e.from); val t = moved.node(e.to)
                if (f.floor == t.floor) e.copy(lengthM = straightLength(f, t)) else e
            } else e
        }
        return moved.copy(edges = edges)
    }

    fun update(b: Building, id: String, change: (Node) -> Node): Building = b.copy(nodes = b.nodes.map { if (it.id == id) change(it) else it })

    fun delete(b: Building, id: String): Building = b.copy(
        nodes = b.nodes.filter { it.id != id },
        edges = b.edges.filter { it.from != id && it.to != id },
        anchors = b.anchors.filter { it.node != id },
        demoDestinations = b.demoDestinations.filter { it != id },
    )

    fun disconnect(b: Building, aId: String, bId: String): Building = b.copy(edges = b.edges.filterNot { EdgeRef(aId, bId).matches(it) })

    private fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0
}
