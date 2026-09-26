package com.campusmaps.survey

import com.campusmaps.data.*
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * survey log to a draft [Building]. Frame: origin at the first ENTRANCE (else START) GPS fix, heading 0, so x = east, y = north.
 * Outdoor nodes come from GPS; indoor nodes are dead-reckoned along edges (steps x stride, walking heading), falling back to GPS
 * when the heading spread is over [MAX_SPREAD_DEG]. Anchor and door facings are rounded to 8 compass words. Anything not measured
 * is flagged estimated=true with a note.
 */
object SurveyConverter {
    const val MAX_SPREAD_DEG = 45.0
    const val ELEVATOR_ID = "ELEV-1"
    const val IMAGE_MIN_WIDTH_CM = 40.0
    /** GPS fixes worse than this are not used to size or check anything. */
    const val GOOD_GPS_M = 10.0
    /** An anchor offset beyond this is a typo (CS-20260925-1238 has 300 m); the anchor is placed at its node instead. */
    const val MAX_ANCHOR_OFFSET_M = 20.0
    /** Distance assumed between an elevator-lobby node and the call buttons when a ride has no ELEVATOR node to hang on. */
    const val LOBBY_TO_ELEVATOR_M = 3.0
    private val NAMES = mapOf("KL" to "Klaus Advanced Computing Building", "CS" to "Classroom South", "CSE" to "Student Center East")

    fun convert(logJson: String): Building = convert(SurveyLog.parse(logJson))

    fun convert(log: SurveyLog): Building {
        val code = log.session.building
        val stride = log.session.strideM
        val obs = log.observations
        val nodeObs = obs.filter { it.kind == "node" && it.type != "START" }
        val starts = obs.filter { it.kind == "node" && it.type == "START" }

        // Ids per CLAUDE.md conventions.
        val ids = LinkedHashMap<Int, String>(); val counters = HashMap<String, Int>()
        fun unique(base: String): String { var id = base; var k = 2; while (id in ids.values) id = "$base${k++}"; return id }
        for (o in nodeObs) ids[o.id] = unique(when (o.type) {
            "ENTRANCE" -> "E-" + shortName(o.name ?: "entrance ${o.id}")
            "ROOM" -> "R-" + (Regex("\\d+[A-Z]?").find(o.name.orEmpty())?.value ?: o.id.toString())
            "ELEVATOR" -> "EL-${o.floor}"
            "STAIRS" -> "ST-${o.floor}"
            "INTERSECTION" -> "H" + counters.merge("H", 1, Int::plus)
            else -> "W" + counters.merge("W", 1, Int::plus)
        })

        val originObs = nodeObs.firstOrNull { it.type == "ENTRANCE" && it.position != null } ?: starts.firstOrNull { it.position != null }
            ?: obs.firstOrNull { it.position != null } ?: error("Survey has no GPS fix to anchor the frame")
        val g0 = originObs.position!!
        val origin = Origin("Survey origin: ${originObs.name ?: "observation ${originObs.id}"} (averaged GPS, x east, y north)", g0.lat, g0.lng, 0.0)
        fun project(g: Gps) = Geo.toBuilding(origin, g.lat, g.lng)

        // Positions: GPS outdoors, dead reckoning indoors, GPS fallback.
        data class Pos(val x: Double, val y: Double, val estimated: Boolean, val how: String)
        val pos = HashMap<Int, Pos>()
        for (o in nodeObs) if (o.outdoor == true) o.position?.let { val (x, y) = project(it); pos[o.id] = Pos(x, y, false, "GPS ±%.0f m".format(it.accuracyM ?: 0.0)) }
        val edgeObs = obs.filter { it.kind == "edge" && it.from != null && it.to != null }
        fun goodGps(id: Int?) = obs.firstOrNull { it.id == id }?.position?.takeIf { (it.accuracyM ?: 0.0) <= GOOD_GPS_M }
        // Survey pace of this session (median of the walks with steps), used only to size a walk whose step counter missed.
        val pace = edgeObs.filter { (it.steps ?: 0) > 0 && (it.durationSec ?: 0.0) > 0 && stride != null }
            .map { it.steps!! * stride!! / it.durationSec!! }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        /** Walks with 0 steps (step counter missed, e.g. walk #33): the step count says nothing, so estimate. */
        fun zeroSteps(e: Observation) = (e.steps ?: 0) == 0 && (e.distanceM ?: 0.0) <= 0.0
        fun gpsLength(e: Observation): Double? {
            val a = goodGps(e.from) ?: return null; val b = goodGps(e.to) ?: return null
            return Geo.haversineM(a.lat, a.lng, b.lat, b.lng)
        }
        fun length(e: Observation): Double = when {
            stride != null && (e.steps ?: 0) > 0 -> e.steps!! * stride
            !zeroSteps(e) -> e.distanceM ?: 0.0
            else -> gpsLength(e) ?: pace?.let { p -> e.durationSec?.let { it * p } } ?: 0.0
        }
        var changed = true
        while (changed) {
            changed = false
            for (e in edgeObs) {
                val h = e.headingMeanDeg ?: continue
                if ((e.headingSpreadDeg ?: 0.0) > MAX_SPREAD_DEG || zeroSteps(e)) continue
                val d = length(e); val dx = d * sin(Math.toRadians(h)); val dy = d * cos(Math.toRadians(h))
                val from = e.from!!; val to = e.to!!; val a = pos[from]; val b = pos[to]
                if (a != null && b == null && to in ids) { pos[to] = Pos(a.x + dx, a.y + dy, a.estimated, "dead-reckoned from ${ids[from]} along edge #${e.id}"); changed = true }
                if (b != null && a == null && from in ids) { pos[from] = Pos(b.x - dx, b.y - dy, b.estimated, "dead-reckoned from ${ids[to]} along edge #${e.id}"); changed = true }
            }
        }
        for (o in nodeObs) if (o.id !in pos) pos[o.id] = o.position?.let { val (x, y) = project(it); Pos(x, y, true, "indoor GPS ±%.0f m, place from the sketch".format(it.accuracyM ?: 0.0)) }
            ?: Pos(0.0, 0.0, true, "no position; place from the sketch")

        val nodes = mutableListOf<Node>()
        for (o in nodeObs) {
            val p = pos.getValue(o.id); val notes = mutableListOf("obs #${o.id}", p.how)
            val type = when (o.type) { "ENTRANCE" -> NodeType.ENTRANCE; "ROOM" -> NodeType.ROOM; "ELEVATOR" -> NodeType.ELEVATOR
                "STAIRS" -> NodeType.STAIRS; "INTERSECTION" -> NodeType.INTERSECTION; else -> NodeType.WAYPOINT }
            var doorFacing: String? = null
            if (type == NodeType.ENTRANCE) {
                notes += "access hours not surveyed (missing means public)"
                if ((o.headingSpreadDeg ?: 0.0) > MAX_SPREAD_DEG) notes += "facing-out heading unreliable (spread %.0f°)".format(o.headingSpreadDeg)
            }
            if (type == NodeType.ROOM && o.doorSide != null && o.headingMeanDeg != null) {
                val turn = when (o.doorSide.uppercase()) { "LEFT" -> -90.0; "RIGHT" -> 90.0; else -> 0.0 }
                doorFacing = Geo.compassWord(o.headingMeanDeg + turn + 180) // the door faces back toward the walker
                if ((o.headingSpreadDeg ?: 0.0) > MAX_SPREAD_DEG) notes += "doorFacing from a heading that wandered ±%.0f°; check".format(o.headingSpreadDeg)
            }
            if (type == NodeType.ELEVATOR) notes += "elevatorId assumed $ELEVATOR_ID"
            if (!o.note.isNullOrBlank()) notes += "surveyor: ${o.note}"
            val g = o.position.takeIf { type == NodeType.ENTRANCE && o.outdoor == true }
            nodes += Node(ids.getValue(o.id), type, o.name ?: ids.getValue(o.id), o.floor, r(p.x), r(p.y),
                elevatorId = ELEVATOR_ID.takeIf { type == NodeType.ELEVATOR }, doorFacing = doorFacing,
                lat = g?.lat, lng = g?.lng, headingDeg = o.headingMeanDeg.takeIf { type == NodeType.ENTRANCE },
                indoor = type == NodeType.ENTRANCE && o.outdoor == false, estimated = p.estimated, notes = notes.joinToString("; "))
        }

        val edges = mutableListOf<Edge>()
        for (e in edgeObs) {
            val from = ids[e.from] ?: continue; val to = ids[e.to] ?: continue
            val notes = mutableListOf("obs #${e.id}, ${e.steps ?: "?"} steps")
            if (zeroSteps(e)) notes += if (gpsLength(e) != null) "0 steps in %.0f s (step counter missed); length is the GPS straight line between the ends"
                .format(e.durationSec ?: 0.0) else "0 steps in %.0f s (step counter missed); length = duration x this session's survey pace %.2f m/s"
                .format(e.durationSec ?: 0.0, pace ?: 0.0)
            if ((e.headingSpreadDeg ?: 0.0) > MAX_SPREAD_DEG)
                notes += "heading wandered ±%.0f°: probably a turn inside the walk; split it with an intersection node".format(e.headingSpreadDeg)
            val ga = goodGps(e.from); val gb = goodGps(e.to)
            if (ga != null && gb != null && !zeroSteps(e)) {
                val gd = Geo.haversineM(ga.lat, ga.lng, gb.lat, gb.lng)
                if (abs(gd - length(e)) > 0.3 * length(e)) notes += "GPS says %.0f m between the ends, walked %.0f m".format(gd, length(e))
            }
            edges += Edge(from, to, r(length(e)), EdgeKind.HALLWAY, estimated = zeroSteps(e), notes = notes.joinToString("; "))
        }

        // Elevator and stairs rides become vertical edges between per-floor nodes.
        val elevatorRides = obs.filter { it.kind == "elevator" && it.fromFloor != null && it.toFloor != null && it.fromFloor != it.toFloor }
        val stairClimbs = obs.filter { it.kind == "stairs" && it.fromFloor != null && it.toFloor != null && it.fromFloor != it.toFloor }
        fun verticalNode(prefix: String, floor: Int, o: Observation, type: NodeType): String {
            val id = "$prefix-$floor"
            if (nodes.none { it.id == id }) {
                // 0.3 logs have no ELEVATOR node; the surveyor saves an INTERSECTION named "... elevator lobby" instead. Hang on it.
                val word = if (type == NodeType.ELEVATOR) "elevator" else "stair"
                val lobby = nodes.firstOrNull { it.floor == floor && it.type != type && word in it.name.lowercase() }
                // Step LOBBY_TO_ELEVATOR_M along the heading the surveyor faced when saving the lobby node (facing the doors), else east.
                val face = lobby?.let { l -> nodeObs.firstOrNull { ids[it.id] == l.id }?.headingMeanDeg }?.let { Math.toRadians(it) } ?: (Math.PI / 2)
                val (x, y) = lobby?.let { it.x + LOBBY_TO_ELEVATOR_M * sin(face) to it.y + LOBBY_TO_ELEVATOR_M * cos(face) }
                    ?: o.position?.let(::project) ?: (0.0 to 0.0)
                nodes += Node(id, type, "${type.name.lowercase().replaceFirstChar(Char::uppercase)}, floor $floor", floor, r(x), r(y),
                    elevatorId = ELEVATOR_ID.takeIf { type == NodeType.ELEVATOR }, estimated = true,
                    notes = "created from ${o.kind} obs #${o.id}; " + (lobby?.let { "placed %.0f m from ${it.id} (${it.name}) along its saved heading, joined by an estimated edge".format(LOBBY_TO_ELEVATOR_M) }
                        ?: "position from indoor GPS, place from the sketch and connect to a hallway"))
                if (lobby != null) edges += Edge(lobby.id, id, LOBBY_TO_ELEVATOR_M, EdgeKind.HALLWAY, estimated = true,
                    notes = "estimated: no edge walk between ${lobby.name} and the ${word}s")
            }
            return id
        }
        for ((list, prefix, kind, type) in listOf(Quad(elevatorRides, "EL", EdgeKind.ELEVATOR, NodeType.ELEVATOR), Quad(stairClimbs, "ST", EdgeKind.STAIRS, NodeType.STAIRS)))
            for (o in list) {
                val from = o.fromFloor!!; val to = o.toFloor!!
                val a = verticalNode(prefix, from, o, type); val b = verticalNode(prefix, to, o, type)
                if (edges.none { setOf(it.from, it.to) == setOf(a, b) }) edges += Edge(a, b, 0.0, kind, abs(to - from), notes = "obs #${o.id} ($from -> $to)")
            }
        val elevators = if (elevatorRides.isEmpty()) emptyList() else listOf(ElevatorSpec(ELEVATOR_ID,
            avgWaitSec = r(elevatorRides.mapNotNull { it.waitSec }.average().takeIf { !it.isNaN() } ?: 35.0),
            worstWaitSec = r(elevatorRides.mapNotNull { it.waitSec }.maxOrNull() ?: 70.0),
            secondsPerFloor = r(elevatorRides.mapNotNull { o -> o.rideSec?.let { it / abs(o.toFloor!! - o.fromFloor!!) } }.average().takeIf { !it.isNaN() } ?: 6.0),
            estimated = elevatorRides.none { it.waitSec != null }, notes = "${elevatorRides.size} rides"))
        /** Seconds per floor over all timed climbs in one direction: total seconds / total floors (a 5-floor climb weighs 5x). */
        fun perFloor(up: Boolean): Double? = stairClimbs.filter { (it.toFloor!! > it.fromFloor!!) == up && it.durationSec != null }
            .let { l -> if (l.isEmpty()) null else l.sumOf { it.durationSec!! } / l.sumOf { abs(it.toFloor!! - it.fromFloor!!) } }
        val stairsUp = perFloor(true); val stairsDown = perFloor(false)

        val anchorObs = obs.filter { it.kind == "anchor" }
        // CS-20260925-1238 logged CS-A02 twice (#12, #13, identical): keep the first of each anchorId.
        val anchors = anchorObs.filter { o -> o.anchorId == null || anchorObs.first { it.anchorId == o.anchorId } === o }.map { o ->
            val id = o.anchorId ?: "$code-A%02d".format(o.id)
            val notes = mutableListOf("obs #${o.id}")
            anchorObs.filter { it !== o && it.anchorId != null && it.anchorId == o.anchorId }.forEach { notes += "duplicate obs #${it.id} ignored" }
            val node = o.nearest?.let { ids[it] } ?: run {
                notes += "no nearest node recorded; picked the closest"
                val (ax, ay) = o.position?.let(::project) ?: (0.0 to 0.0)
                nodes.filter { it.floor == o.floor }.minByOrNull { hypot(it.x - ax, it.y - ay) }?.id ?: nodes.first().id
            }
            val n = nodes.first { it.id == node }
            val offset = when {
                o.offsetFromNodeM == null -> 0.0.also { notes += "offsetFromNodeM not recorded; placed at the node" }
                o.offsetFromNodeM > MAX_ANCHOR_OFFSET_M -> 0.0.also { notes += "offsetFromNodeM %.0f m is not believable; placed at the node".format(o.offsetFromNodeM) }
                else -> o.offsetFromNodeM
            }
            val f = o.facingDeg?.let { Math.toRadians(it) }
            if (o.facingDeg != null) notes += "facingDeg %.1f".format(o.facingDeg) else notes += "facing not recorded"
            val kind = if ((o.widthCm ?: 0.0) >= IMAGE_MIN_WIDTH_CM) AnchorKind.IMAGE else AnchorKind.TEXT
            if (o.widthCm == null) notes += "width not measured"
            if (o.widthEstimated == true) notes += "width estimated, not taped"
            if (o.heightCm == null) notes += "height not measured"
            val text = o.text?.trim()
            Anchor(id, node, kind, image = "anchors/$code/$id.jpg".takeIf { kind == AnchorKind.IMAGE }, widthM = o.widthCm?.let { it / 100 },
                x = r(n.x - offset * (f?.let(::sin) ?: 0.0)), y = r(n.y - offset * (f?.let(::cos) ?: 0.0)), floor = o.floor,
                heightM = o.heightCm?.let { it / 100 }, facing = o.facingDeg?.let(Geo::compassWord), text = text,
                aliases = listOfNotNull(o.ocr?.read, o.ocrFar?.read).map { it.trim().uppercase() }.filter { it.isNotEmpty() && it != text?.uppercase() }.distinct(),
                description = o.note?.takeIf { it.isNotBlank() } ?: "Surveyed ${o.snapshot?.at?.take(10) ?: ""}".trim(),
                estimated = o.widthCm == null || o.widthEstimated == true || o.facingDeg == null, notes = notes.joinToString("; "))
        }

        val startPoints = starts.mapIndexedNotNull { i, o -> o.position?.let { StartPoint("P${i + 1}", o.name ?: "P${i + 1}", it.lat, it.lng) } }
        return Building(code, NAMES[code] ?: code, origin,
            stairsSecondsPerFloor = stairsUp?.let(::r) ?: 22.0, stairsDownSecondsPerFloor = stairsDown?.let(::r) ?: 18.0,
            elevators = elevators, nodes = nodes, edges = edges, anchors = anchors, startPoints = startPoints,
            notes = "DRAFT from survey session ${log.session.id} (${log.session.startedAt}), stride ${stride ?: "unknown"} m" +
                (if (log.session.strideCalibrated) " (calibrated${log.session.strideMethod?.let { " by $it" } ?: ""})" else " (NOT calibrated)") +
                ". Walking speed is the default; stairs up ${stairsUp?.let { "%.1f s per floor measured".format(it) } ?: "default"}, " +
                "down ${stairsDown?.let { "%.1f s per floor measured".format(it) } ?: "default"}. Anything marked estimated is a guess.")
    }

    /** Short id part from a name: initials of words (digits kept whole), ignoring "entrance". "95 Decatur Street entrance" -> "95DS". */
    fun shortName(name: String): String = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() && !it.equals("entrance", true) }
        .joinToString("") { if (it[0].isDigit()) it else it[0].uppercase() }.ifEmpty { "X" }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private fun r(v: Double) = Math.round(v * 100) / 100.0

    fun report(logJson: String): String = report(SurveyLog.parse(logJson))

    /** What the survey has versus the docs/02 target for its building, plus validator problems on the draft. */
    fun report(log: SurveyLog): String = buildString {
        val b = convert(log)
        val code = b.code
        appendLine("Survey ${log.session.id} for $code (${b.name}): ${b.nodes.size} nodes, ${b.edges.size} edges, ${b.anchors.size} anchors")
        val entrances = b.nodes.filter { it.type == NodeType.ENTRANCE }
        appendLine("Entrances: ${entrances.size} " + entrances.groupBy { it.floor }.map { (f, l) -> "floor $f: ${l.size}" })
        val missing = mutableListOf<String>()
        val target = TARGETS[code]
        if (target != null) {
            if (entrances.size < target.entrances) missing += "entrances: ${entrances.size} of ${target.entrances}"
            for ((floor, count) in target.entrancesPerFloor) if (entrances.count { it.floor == floor } < count)
                missing += "entrances on floor $floor: ${entrances.count { it.floor == floor }} of $count (${target.entranceNote})"
            if (target.indoorEntrance && entrances.none { it.indoor }) missing += "indoor entrance (Library South connection, outdoor=false)"
            for (room in target.rooms) if (b.nodes.none { it.id == "R-$room" }) missing += "room node R-$room"
            if (target.floors.size > 1) for (floor in target.floors) {
                if (b.nodes.none { it.type == NodeType.ELEVATOR && it.floor == floor }) missing += "elevator node on floor $floor"
                if (b.nodes.none { it.type == NodeType.STAIRS && it.floor == floor }) missing += "stairs node on floor $floor"
            }
            for (floor in target.floors) {
                val n = b.anchors.count { it.floor == floor }
                if (n < 2) missing += "anchors on floor $floor: $n of at least 2"
            }
            if (b.anchors.size < target.anchors) missing += "anchors: ${b.anchors.size} of ${target.anchors} on the routes"
            if (b.nodes.size < target.nodes) missing += "nodes: ${b.nodes.size} of about ${target.nodes}"
            if (target.floors.size > 1 && b.elevators.isEmpty()) missing += "elevator timings (5 rides ${target.floors.first()} -> ${target.floors.last()}: wait and ride)"
            if (target.floors.size > 1 && log.observations.none { it.kind == "stairs" }) missing += "stairs timings (one climb per floor pair)"
            if (target.startPoints && log.observations.none { it.kind == "node" && it.type == "START" }) missing += "START points P1 and P2 (GPS)"
        }
        if (entrances.any { it.access == null }) missing += "posted hours / card rule photos for ${entrances.filter { it.access == null }.joinToString { it.id }}"
        if (log.observations.none { it.kind == "note" }) missing += "notes (posted hours, card readers)"
        if (!log.session.strideCalibrated) missing += "calibrated stride"
        val climbs = log.observations.filter { it.kind == "stairs" && it.fromFloor != null && it.toFloor != null }
        if (climbs.isNotEmpty() && climbs.none { it.toFloor!! > it.fromFloor!! })
            missing += "an UPWARD stairs climb (only descents timed; stairsSecondsPerFloor stays the default)"
        appendLine("Missing versus docs/02 target:")
        missing.forEach { appendLine("  - $it") }
        val warnings = b.nodes.filter { it.estimated }.map { "node ${it.id}: ${it.notes}" } +
            b.edges.filter { it.notes?.contains("wandered") == true || it.notes?.contains("GPS says") == true }.map { "edge ${it.from}-${it.to}: ${it.notes}" } +
            b.anchors.filter { it.notes?.contains("not") == true }.map { "anchor ${it.id}: ${it.notes}" }
        val zero = log.observations.filter { it.kind == "edge" && (it.steps ?: 0) == 0 && (it.distanceM ?: 0.0) <= 0.0 }
            .map { "walk #${it.id}: 0 steps in %.0f s, length estimated".format(it.durationSec ?: 0.0) }
        val dupes = log.observations.filter { it.kind == "anchor" && it.anchorId != null }.groupBy { it.anchorId }.filter { it.value.size > 1 }
            .map { (a, l) -> "anchor $a logged ${l.size} times (obs ${l.joinToString { "#${it.id}" }}); first kept" }
        val offsets = log.observations.filter { it.kind == "anchor" && (it.offsetFromNodeM ?: 0.0) > MAX_ANCHOR_OFFSET_M }
            .map { "anchor ${it.anchorId}: offsetFromNodeM ${it.offsetFromNodeM} m ignored (typo?)" }
        val all = warnings + zero + dupes + offsets
        if (all.isNotEmpty()) { appendLine("Warnings:"); all.forEach { appendLine("  - $it") } }
        val videos = log.observations.filter { it.kind == "video" }
        if (videos.isNotEmpty()) {
            appendLine("Walkthrough videos (not converted; reconstruct missing legs from their sensors.json by hand):")
            videos.forEach { appendLine("  - ${it.file}: ${it.label ?: ""} (%.0f s, floor ${it.floor})".format(it.durationSec ?: 0.0)) }
        }
        val problems = BuildingValidator.validate(b)
        if (problems.isNotEmpty()) { appendLine("Validator on the draft:"); problems.forEach { appendLine("  - $it") } }
    }

    private data class Target(val entrances: Int, val entrancesPerFloor: Map<Int, Int>, val entranceNote: String, val indoorEntrance: Boolean,
                              val rooms: List<String>, val floors: List<Int>, val anchors: Int, val nodes: Int, val startPoints: Boolean)
    private val TARGETS = mapOf(
        "CS" to Target(5, mapOf(1 to 3, 2 to 2), "docs say three on floor 1, two on floor 2", true, listOf("150", "608"), listOf(1, 2, 6), 8, 50, true),
        "KL" to Target(1, emptyMap(), "", false, emptyList(), listOf(1), 8, 15, false),
        "CSE" to Target(2, emptyMap(), "", false, listOf("220"), listOf(1, 2), 4, 10, false),
    )
}

/**
 * Carry the hand-tuned parts of the current building file over to a fresh survey draft (docs/19 "Klaus refresh, step by step").
 * The survey wins on geometry and timings; the old file wins on what the survey cannot know.
 */
object HandTuned {
    data class Result(val building: Building, val log: List<String>)

    fun keep(draft: Building, old: Building): Result {
        val log = mutableListOf<String>()
        // 1. Node ids the app relies on (S1, S2, T, R-1116, ...): a draft node of the same type named like an old id or old name takes that id.
        val rename = HashMap<String, String>()
        val taken = draft.nodes.map { it.id }.toMutableSet()
        for (o in old.nodes) {
            if (o.id in taken) continue
            val hit = draft.nodes.firstOrNull { d -> d.id !in rename && d.type == o.type &&
                (d.name.equals(o.id, true) || d.name.equals(o.name, true)) } ?: continue
            rename[hit.id] = o.id; taken -= hit.id; taken += o.id
            log += "renamed draft node ${hit.id} (\"${hit.name}\") to ${o.id}"
        }
        fun id(x: String) = rename[x] ?: x
        var nodes = draft.nodes.map { if (it.id in rename) it.copy(id = id(it.id)) else it }
        val edges = draft.edges.map { it.copy(from = id(it.from), to = id(it.to)) }
        val anchors = draft.anchors.map { it.copy(node = id(it.node)) }
        // 2. Access windows (posted hours) per entrance, matched by id then by name; the survey never records them.
        nodes = nodes.map { n ->
            if (n.type != NodeType.ENTRANCE || n.access != null) return@map n
            val o = old.nodeOrNull(n.id)?.takeIf { it.type == NodeType.ENTRANCE }
                ?: old.nodes.firstOrNull { it.type == NodeType.ENTRANCE && it.name.equals(n.name, true) }
            if (o?.access == null) n else n.copy(access = o.access).also { log += "kept access windows of ${o.id} on ${n.id}" }
        }
        // 3. Demo destinations: kept when the room exists in the draft, otherwise listed for a hand fix.
        val ids = nodes.map { it.id }.toSet()
        val (kept, lost) = old.demoDestinations.partition { it in ids }
        kept.forEach { log += "kept demo destination $it" }
        lost.forEach { log += "DEMO DESTINATION $it IS NOT IN THE SURVEY: add the room node or change demoDestinations" }
        // 4. Outdoor start points: the survey's START nodes win; otherwise the old ones stay.
        val starts = draft.startPoints.ifEmpty {
            old.startPoints.also { if (it.isNotEmpty()) log += "kept start points ${it.joinToString { p -> p.id }} (survey has no START node)" }
        }
        // 5. Old node ids the app or tests may name that the draft does not have.
        old.nodes.filter { it.id !in ids }.forEach { log += "old node ${it.id} (${it.name}) is not in the draft" }
        return Result(draft.copy(nodes = nodes, edges = edges, anchors = anchors, demoDestinations = kept, startPoints = starts), log)
    }
}

/**
 * `ConvertMain <survey.json | export.zip> <out.json> [--keep <current building.json>]`: writes the draft building and prints the
 * gap report. A survey export zip is read directly (its `survey.json`). With `--keep`, hand-tuned fields of the current file
 * (node ids such as S1/S2/T, demoDestinations, startPoints, access windows) are carried over, see [HandTuned].
 */
object ConvertMain {
    fun readLog(path: File): String =
        if (path.name.endsWith(".zip", true)) java.util.zip.ZipFile(path).use { z ->
            val entry = z.entries().asSequence().firstOrNull { !it.isDirectory && it.name.substringAfterLast('/') == "survey.json" }
                ?: error("No survey.json inside ${path.name}")
            z.getInputStream(entry).use { it.readBytes().decodeToString() }
        } else path.readText()

    @JvmStatic
    fun main(args: Array<String>) {
        val k = args.indexOf("--keep")
        val keep = if (k >= 0) File(args.getOrNull(k + 1) ?: error("--keep needs a file")) else null
        val files = args.filterIndexed { i, _ -> k < 0 || (i != k && i != k + 1) }
        require(files.size == 2) { "usage: ConvertMain <survey.json | export.zip> <out.json> [--keep <current building.json>]" }
        val text = readLog(File(files[0]))
        var draft = SurveyConverter.convert(text)
        println(SurveyConverter.report(text))
        if (keep != null) {
            val r = HandTuned.keep(draft, BuildingLoader.fromJson(keep.readText()))
            draft = r.building
            println("Kept from ${keep.name}:"); r.log.forEach { println("  - $it") }
            val problems = BuildingValidator.validate(draft)
            println("Validator on the merged draft:" + if (problems.isEmpty()) " clean" else ""); problems.forEach { println("  - $it") }
        }
        File(files[1]).writeText(BuildingLoader.toJson(draft))
        println("Wrote ${files[1]}")
    }
}
