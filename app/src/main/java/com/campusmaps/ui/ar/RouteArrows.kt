package com.campusmaps.ui.ar

import kotlin.math.atan2
import kotlin.math.hypot

/*
 * Pure layout of the AR content in building coordinates (docs/05 "What we draw"). No Android types and no app types,
 * so this file ports as is to any codebase that can list the route as building-frame points.
 */

/** A route vertex in the building frame (metres; x east-ish, y north-ish; integer floor). */
data class RoutePoint(val x: Double, val y: Double, val floor: Int)

/** One arrow on the floor, in building coordinates. [dirX], [dirY] is the unit walking direction. */
data class FloorArrow(val x: Double, val y: Double, val dirX: Double, val dirY: Double) {
    /** SceneView yaw (degrees about +Y) that turns a chevron modelled pointing local -Z into this direction. */
    val yawDeg: Double get() = Math.toDegrees(atan2(-dirX, dirY))
}

/** Where "Place route here" pins the route: the user's point, its floor, and the direction they should face. */
data class Placement(
    val label: String, val floor: Int, val x: Double, val y: Double, val dirX: Double, val dirY: Double,
    /** Compass bearing you face walking in, when this node is an outdoor entrance (auto-placement at the entrance snap). */
    val walkInDeg: Double? = null,
)

/** Destination marker: a post and a floating label [heightM] above the floor. */
data class ArDestination(val label: String, val x: Double, val y: Double, val floor: Int)

/** Colour of the card door sign: amber when the user carries the card, red when the door will not open for them. */
enum class DoorSignTone { ATTENTION, BLOCKED }

/**
 * Floating sign at an entrance that is card-only right now ("Tap your PantherCard" or "PantherCard required"),
 * drawn like [ArDestination]: a post and a label above the door, in building coordinates.
 */
data class ArDoorSign(val label: String, val x: Double, val y: Double, val floor: Int, val tone: DoorSignTone)

/**
 * Everything the AR layer needs, in building coordinates. Build it from whatever the host app uses for routes
 * (our UiState: [fromUiState] in ArInputs.kt; the teammate's GuidanceEngine: see docs/05 "Porting").
 */
data class ArRouteInput(
    /** The whole route polyline (all floors). Arrows are laid on [floor] only. */
    val points: List<RoutePoint>,
    /** Floor the user is on now. */
    val floor: Int,
    /** The big next-turn arrow, if the next decision is a turn on this floor. */
    val nextTurn: FloorArrow? = null,
    val destination: ArDestination? = null,
    /** The user's current point and facing direction, for the debug place flow. Null hides the button. */
    val placement: Placement? = null,
    val floorHeightM: Double = 3.8,
    /** The card-only entrance on this route, if any. */
    val cardDoor: ArDoorSign? = null,
)

object RouteArrows {
    /** Distance between floor chevrons along the route (Live View size arrows need more room than the old 1.5 m). */
    const val SPACING_M = 2.5

    /**
     * Chevrons every [spacing] m along the whole route on [floor] (world-locked: they do not depend on progress, so
     * fake-walk steps do not move them). Spacing carries across vertices, so a corner does not bunch arrows up.
     * Arrows within [clearRadius] of [clearAround] are dropped to leave room for the big next-turn arrow.
     */
    fun chain(
        points: List<RoutePoint>, floor: Int, spacing: Double = SPACING_M,
        clearAround: Pair<Double, Double>? = null, clearRadius: Double = 1.2,
    ): List<FloorArrow> {
        val out = mutableListOf<FloorArrow>()
        var carry = spacing / 2 // first arrow half a step from the start
        for ((a, c) in points.zipWithNext()) {
            if (a.floor != floor || c.floor != floor) { carry = spacing / 2; continue }
            val len = hypot(c.x - a.x, c.y - a.y)
            if (len < 1e-6) continue
            val ux = (c.x - a.x) / len; val uy = (c.y - a.y) / len
            var s = carry
            while (s <= len) {
                val x = a.x + ux * s; val y = a.y + uy * s
                val clear = clearAround?.let { hypot(x - it.first, y - it.second) < clearRadius } ?: false
                if (!clear) out += FloorArrow(x, y, ux, uy)
                s += spacing
            }
            carry = s - len
        }
        return out
    }

    /** Arrow at vertex [i] pointing along the outgoing edge; null at the end, across a floor change, or on a zero edge. */
    fun arrowAt(points: List<RoutePoint>, i: Int): FloorArrow? {
        val n = points.getOrNull(i) ?: return null
        val next = points.getOrNull(i + 1) ?: return null
        if (n.floor != next.floor) return null
        val len = hypot(next.x - n.x, next.y - n.y)
        if (len < 1e-6) return null
        return FloorArrow(n.x, n.y, (next.x - n.x) / len, (next.y - n.y) / len)
    }

    /**
     * The placement for vertex [i]: its position and the direction of the next same-floor edge, or the previous edge
     * reversed at the last vertex.
     */
    fun placementAt(points: List<RoutePoint>, i: Int, label: String): Placement? {
        val n = points.getOrNull(i) ?: return null
        fun dirTo(j: Int): Pair<Double, Double>? {
            val o = points.getOrNull(j) ?: return null
            if (o.floor != n.floor) return null
            val len = hypot(o.x - n.x, o.y - n.y)
            return if (len < 1e-6) null else ((o.x - n.x) / len to (o.y - n.y) / len)
        }
        val d = ((i + 1)..points.lastIndex).asSequence().takeWhile { points[it].floor == n.floor }.firstNotNullOfOrNull(::dirTo)
            ?: dirTo(i - 1)?.let { (x, y) -> -x to -y }
            ?: return null
        return Placement(label, n.floor, n.x, n.y, d.first, d.second)
    }
}
