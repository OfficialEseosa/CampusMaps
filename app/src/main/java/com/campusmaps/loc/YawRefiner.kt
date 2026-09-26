package com.campusmaps.loc

import kotlin.math.abs
import kotlin.math.hypot

/** A route vertex in the building frame (metres, y north-ish, integer floor). */
data class PathPoint(val x: Double, val y: Double, val floor: Int)

/** Where the route's heading came from, for the debug card and the reroute tolerance. */
enum class YawSource { NONE, COMPASS, TAP, SIGN }

/** What the debug card shows ("yaw: compass, unrefined" / "yaw: compass, refined 18 deg"). */
data class YawStatus(
    val source: YawSource = YawSource.NONE,
    /** True once the first straight 3 m has corrected the compass heading. */
    val refined: Boolean = false,
    /** Total correction applied so far, degrees (positive = the route turned clockwise seen from above). */
    val correctionDeg: Double = 0.0,
) {
    val compassPlaced: Boolean get() = source == YawSource.COMPASS

    fun debugLine(): String = when (source) {
        YawSource.NONE -> "yaw: not placed"
        YawSource.TAP -> if (refined) "yaw: floor tap, refined %.0f deg".format(correctionDeg) else "yaw: floor tap"
        YawSource.SIGN -> "yaw: sign fix"
        YawSource.COMPASS -> if (refined) "yaw: compass, refined %.0f deg".format(correctionDeg) else "yaw: compass, unrefined"
    }
}

/**
 * Heading self-correction after a compass placement. Pure math, JVM-tested (YawRefinerTest).
 *
 * In plain words: indoors the compass is often 15 to 30 degrees off, so after 20 m the camera's position drifts 5 to 10 m
 * beside the route. But the user walks along the route. So once they have walked [MIN_TRAVEL_M] in a fairly straight line
 * (every point within [MAX_CHORD_DEV_M] of the line between the ends), the direction they walked is the direction the route
 * goes there. The building is turned by the difference, around the placement point (the start node stays under the spot
 * where the user started). The first time it takes the whole difference (at most [MAX_CORRECTION_DEG]); after that it
 * takes [SLOW_GAIN] of the difference per straight 3 m, so one detour round a pillar cannot swing the route.
 *
 * A floor tap ([startTap]) is refined the same way with a tighter cap ([MAX_TAP_CORRECTION_DEG]): the phone's heading at
 * the tap is often 5 to 10 degrees off the hallway, which puts the route 1 to 3 m sideways 15 m on. A sign fix stops it for
 * good ([stop]); a sign is better than walking.
 *
 * Angles: yaw as in [BuildingToWorld] (Ry, radians); log and status in degrees.
 */
class YawRefiner {
    data class Result(
        val transform: BuildingToWorld,
        /** Correction applied now, degrees. */
        val deltaDeg: Double,
        /** Walked since the placement, metres (path length). */
        val travelM: Double,
        /** True for the first (full) correction. */
        val first: Boolean,
        /** Measured difference before the cap and the slow filter, degrees. */
        val residualDeg: Double,
    )

    var status: YawStatus = YawStatus()
        private set

    private var active = false
    private var pivotX = 0.0
    private var pivotY = 0.0
    private val window = ArrayDeque<Pair<Double, Double>>()
    private var travelM = 0.0
    /** Largest total correction for the current placement, degrees. */
    private var maxDeg = MAX_CORRECTION_DEG

    /** A compass (or door heading) placement put building point ([pivotX], [pivotY]) under the camera at ([camX], [camZ]). */
    fun startCompass(pivotX: Double, pivotY: Double, camX: Double, camZ: Double) =
        start(YawSource.COMPASS, MAX_CORRECTION_DEG, pivotX, pivotY, camX, camZ)

    /** A floor tap put building point ([pivotX], [pivotY]) at the tapped spot; the camera is at ([camX], [camZ]). */
    fun startTap(pivotX: Double, pivotY: Double, camX: Double, camZ: Double) =
        start(YawSource.TAP, MAX_TAP_CORRECTION_DEG, pivotX, pivotY, camX, camZ)

    private fun start(source: YawSource, maxDeg: Double, pivotX: Double, pivotY: Double, camX: Double, camZ: Double) {
        this.pivotX = pivotX; this.pivotY = pivotY
        this.maxDeg = maxDeg
        active = true
        window.clear(); window.addLast(camX to camZ)
        travelM = 0.0
        status = YawStatus(source)
    }

    /** A sign fix (or anything better than walking) set the transform: no more refining. */
    fun stop(source: YawSource) {
        active = false
        window.clear()
        status = YawStatus(source)
    }

    /** The AR view left: the world frame is gone. */
    fun reset() = stop(YawSource.NONE)

    /**
     * One camera position (world X, Z). [t] is the current transform, [route] the whole route polyline in the building frame,
     * [floor] the floor the user is on. Returns the corrected transform when a correction was made, else null.
     */
    fun onCamera(camX: Double, camZ: Double, t: BuildingToWorld, route: List<PathPoint>, floor: Int): Result? {
        if (!active) return null
        val last = window.lastOrNull()
        if (last == null) { window.addLast(camX to camZ); return null }
        val step = hypot(camX - last.first, camZ - last.second)
        if (step < SAMPLE_STEP_M) return null
        window.addLast(camX to camZ)
        travelM += step

        // Slide the start forward until the window is straight, while it still spans 3 m.
        while (window.size >= 2 && span() >= MIN_TRAVEL_M && !straight(window.toList(), MAX_CHORD_DEV_M)) window.removeFirst()
        if (span() < MIN_TRAVEL_M) return null

        val a = window.first(); val b = window.last()
        window.clear(); window.addLast(b) // next measurement starts here
        val chordLen = hypot(b.first - a.first, b.second - a.second)

        // Where the route goes over the same distance, starting where the window started.
        val (sx, sy) = t.toBuilding(Vec3(a.first, 0.0, a.second))
        val along = RoutePath.project(route, floor, sx, sy) ?: return null
        val sub = RoutePath.slice(route, along, along + chordLen) ?: return null
        if (sub.any { it.floor != floor }) return null
        val p0 = sub.first(); val p1 = sub.last()
        if (!straight(sub.map { it.x to it.y }, MAX_CHORD_DEV_M)) return null // the route turns here: nothing to compare
        val w0 = t.toWorld(p0.x, p0.y, t.refFloor); val w1 = t.toWorld(p1.x, p1.y, t.refFloor)
        val ex = w1.x - w0.x; val ez = w1.z - w0.z
        if (hypot(ex, ez) < 1.0) return null

        val residual = Math.toDegrees(BuildingToWorld.wrapPi(
            BuildingToWorld.yawOf(b.first - a.first, b.second - a.second) - BuildingToWorld.yawOf(ex, ez)))
        val first = !status.refined
        val wanted = if (first) residual.coerceIn(-maxDeg, maxDeg) else {
            if (abs(residual) > maxDeg) return null // walked another way (a detour): ignore
            residual * SLOW_GAIN
        }
        val total = (status.correctionDeg + wanted).coerceIn(-maxDeg, maxDeg)
        val delta = total - status.correctionDeg
        status = status.copy(refined = true, correctionDeg = total)
        return Result(rotateAbout(t, Math.toRadians(delta), pivotX, pivotY), delta, travelM, first, residual)
    }

    private fun span(): Double {
        val a = window.first(); val b = window.last()
        return hypot(b.first - a.first, b.second - a.second)
    }

    companion object {
        /** Straight walk needed before a measurement, metres. */
        const val MIN_TRAVEL_M = 3.0
        /** Every point of a "straight" walk stays this close to the line between its ends, metres. */
        const val MAX_CHORD_DEV_M = 1.0
        /** Largest total correction of the compass heading, degrees. */
        const val MAX_CORRECTION_DEG = 45.0
        /** Largest total correction of a floor tap's heading, degrees (a tap is closer than the compass to start with). */
        const val MAX_TAP_CORRECTION_DEG = 15.0
        /** After the first correction, this fraction of each new difference is applied (per straight 3 m). */
        const val SLOW_GAIN = 0.1
        /** Camera positions closer than this to the previous one are skipped, metres. */
        const val SAMPLE_STEP_M = 0.25

        /** True when every point is within [maxDev] of the line from the first to the last. */
        fun straight(pts: List<Pair<Double, Double>>, maxDev: Double): Boolean {
            if (pts.size < 3) return true
            val (ax, az) = pts.first(); val (bx, bz) = pts.last()
            val dx = bx - ax; val dz = bz - az; val l = hypot(dx, dz)
            if (l < 1e-9) return false
            return pts.all { (x, z) -> abs((x - ax) * dz - (z - az) * dx) / l <= maxDev }
        }

        /** [t] turned by [deltaRad] about building point ([px], [py]): that point stays where it is in the world. */
        fun rotateAbout(t: BuildingToWorld, deltaRad: Double, px: Double, py: Double): BuildingToWorld {
            val w = t.toWorld(px, py, t.refFloor)
            val r = t.copy(yawRad = BuildingToWorld.wrapPi(t.yawRad + deltaRad))
            val w2 = r.toWorld(px, py, r.refFloor)
            return r.copy(tx = r.tx + w.x - w2.x, tz = r.tz + w.z - w2.z)
        }
    }
}

/** Distances along a route polyline seen from above (vertical hops count their horizontal run). */
object RoutePath {
    private fun cum(route: List<PathPoint>): DoubleArray {
        val c = DoubleArray(route.size)
        for (i in 1 until route.size) c[i] = c[i - 1] + hypot(route[i].x - route[i - 1].x, route[i].y - route[i - 1].y)
        return c
    }

    /** Distance along [route] of the closest point to ([x], [y]) on segments that lie on [floor]; null when none does. */
    fun project(route: List<PathPoint>, floor: Int, x: Double, y: Double): Double? {
        if (route.size < 2) return null
        val c = cum(route)
        var best = Double.MAX_VALUE; var bestAlong: Double? = null
        for (i in 0 until route.lastIndex) {
            val a = route[i]; val b = route[i + 1]
            if (a.floor != floor || b.floor != floor) continue
            val dx = b.x - a.x; val dy = b.y - a.y; val l2 = dx * dx + dy * dy
            val u = if (l2 < 1e-9) 0.0 else (((x - a.x) * dx + (y - a.y) * dy) / l2).coerceIn(0.0, 1.0)
            val d = hypot(a.x + u * dx - x, a.y + u * dy - y)
            if (d < best - 1e-9) { best = d; bestAlong = c[i] + u * (c[i + 1] - c[i]) }
        }
        return bestAlong
    }

    /** The route between [from] and [to] metres along it: start point, the vertices in between, end point. Null past the end. */
    fun slice(route: List<PathPoint>, from: Double, to: Double): List<PathPoint>? {
        if (route.size < 2) return null
        val c = cum(route)
        if (to > c.last() + 1e-6) return null
        val out = mutableListOf(at(route, c, from))
        for (i in route.indices) if (c[i] > from + 1e-9 && c[i] < to - 1e-9) out += route[i]
        out += at(route, c, to)
        return out
    }

    private fun at(route: List<PathPoint>, c: DoubleArray, s: Double): PathPoint {
        val i = (0 until route.lastIndex).firstOrNull { c[it + 1] >= s - 1e-9 } ?: (route.lastIndex - 1)
        val a = route[i]; val b = route[i + 1]
        val l = c[i + 1] - c[i]
        val u = if (l < 1e-9) 0.0 else ((s - c[i]) / l).coerceIn(0.0, 1.0)
        return PathPoint(a.x + u * (b.x - a.x), a.y + u * (b.y - a.y), if (u < 0.5) a.floor else b.floor)
    }
}
