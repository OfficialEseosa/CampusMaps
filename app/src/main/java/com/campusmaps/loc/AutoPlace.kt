package com.campusmaps.loc

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Automatic route placement at the start node (no floor tap). Pure math, JVM-tested (AutoPlaceTest).
 *
 * In plain words: the phone knows where it is in ARCore's world (camera position) and which way is north (compass). The
 * route knows the start node and which way the building's +y points on the compass (core Origin.headingDeg). Put the start
 * node on the floor under the camera, turn the building so its compass directions match the phone's, and the arrows line up.
 *
 * Angles here are compass bearings in degrees: 0 = north (or building +y), clockwise.
 */
object AutoPlace {
    /** Camera height above the floor assumed when no floor plane has been found yet. */
    const val FALLBACK_CAMERA_HEIGHT_M = 1.4
    /** TRACKING for this long without a floor plane: place anyway with [FALLBACK_CAMERA_HEIGHT_M]. */
    const val NO_PLANE_WAIT_MS = 3_000L
    /** Compass samples are averaged over this window. */
    const val COMPASS_WINDOW_MS = 1_000L
    /** A plane counts as the floor under the camera when it is this far below it (metres). */
    const val FLOOR_MIN_BELOW_M = 0.5
    const val FLOOR_MAX_BELOW_M = 2.2
    /** At an entrance, the compass is replaced by the door's walk-in heading when the two agree within this. */
    const val ENTRANCE_TRUST_DEG = 60.0

    fun wrap360(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Signed smallest difference a - b in (-180, 180]. */
    fun diffDeg(a: Double, b: Double): Double {
        val d = wrap360(a - b)
        return if (d > 180.0) d - 360.0 else d
    }

    /**
     * Bearing of a horizontal ARCore world direction ([fx], [fz]) measured clockwise from world -Z as seen from above
     * (world +X is 90). Only differences of these matter: the compass offset turns it into a real bearing.
     */
    fun arBearingDeg(fx: Double, fz: Double): Double = wrap360(Math.toDegrees(atan2(fx, -fz)))

    /** Compass bearing (true north) to building bearing: building +y is compass [originHeadingDeg]. */
    fun buildingBearingDeg(compassDeg: Double, originHeadingDeg: Double): Double = wrap360(compassDeg - originHeadingDeg)

    /** Unit building-frame vector (x, y) of a building bearing (+x is 90 degrees clockwise of +y). */
    fun buildingDir(bearingDeg: Double): Pair<Double, Double> {
        val r = Math.toRadians(bearingDeg)
        return sin(r) to cos(r)
    }

    /**
     * World height of the floor under a camera at [camY]: the highest tracked upward plane between 0.5 and 2.2 m below the
     * camera ([planeYs]), else [camY] - 1.4. Second value: true when a plane was used.
     */
    fun floorY(camY: Double, planeYs: List<Double>): Pair<Double, Boolean> {
        val y = planeYs.filter { camY - it in FLOOR_MIN_BELOW_M..FLOOR_MAX_BELOW_M }.maxOrNull()
        return if (y != null) y to true else (camY - FALLBACK_CAMERA_HEIGHT_M) to false
    }

    /**
     * Camera bearing to use at an entrance: always the door's [walkInDeg]. The student is walking in, the door's heading
     * was measured, and the compass by a steel door frame can be 70 degrees off (Classroom South 2026-09-26: compass
     * 288 for a 214 door, which sent the route into the wall). The heading refiner removes any small residual.
     */
    fun entranceBearingDeg(compassDeg: Double?, walkInDeg: Double): Double = wrap360(walkInDeg)

    /**
     * The transform: building point ([bx], [by]) on [floor] is the floor point under the camera ([camX], [floorY], [camZ]),
     * and the camera's horizontal world direction ([fx], [fz]) points at compass [cameraCompassDeg].
     */
    fun transform(
        camX: Double, floorY: Double, camZ: Double, fx: Double, fz: Double,
        cameraCompassDeg: Double, originHeadingDeg: Double,
        bx: Double, by: Double, floor: Int, floorHeightM: Double,
    ): BuildingToWorld? {
        if (hypot(fx, fz) < 1e-6) return null
        val (dx, dy) = buildingDir(buildingBearingDeg(cameraCompassDeg, originHeadingDeg))
        return BuildingToWorld.fromCorrespondence(Vec3(camX, floorY, camZ), fx, fz, bx, by, dx, dy, floor, floorHeightM)
    }
}

/**
 * Pairs compass bearings with ARCore bearings of the same camera direction, so turning the phone during the window does not
 * matter: the offset (compass - ARCore) is what is averaged (circular mean over [windowMs]). Not thread-safe; feed it from
 * the AR frame loop.
 */
class CompassOffset(private val windowMs: Long = AutoPlace.COMPASS_WINDOW_MS, private val minSteadiness: Double = 0.8) {
    private data class S(val t: Long, val s: Double, val c: Double)
    private val samples = ArrayDeque<S>()

    fun add(timeMs: Long, compassDeg: Double, arDeg: Double) {
        val o = Math.toRadians(AutoPlace.diffDeg(compassDeg, arDeg))
        samples.addLast(S(timeMs, sin(o), cos(o)))
        while (samples.isNotEmpty() && timeMs - samples.first().t > windowMs) samples.removeFirst()
    }

    /**
     * Mean offset in degrees (compass = ARCore + offset), or null until the samples span 80% of the window, or while they
     * disagree (resultant length under [minSteadiness]: a magnet nearby or the phone swinging).
     */
    fun offsetDeg(): Double? {
        if (samples.size < 3) return null
        if (samples.last().t - samples.first().t < windowMs * 8 / 10) return null
        val s = samples.sumOf { it.s } / samples.size
        val c = samples.sumOf { it.c } / samples.size
        if (hypot(s, c) < minSteadiness) return null
        return AutoPlace.wrap360(Math.toDegrees(atan2(s, c)))
    }

    fun clear() = samples.clear()
}

/**
 * The "place once" rule. Auto-placement happens at most once per route (keyed by [routeKey]), never over an existing
 * transform, never after a floor tap or a sign fix (they win for the rest of the session), never on the outdoor leg.
 */
class AutoPlaceGate(private val noPlaneWaitMs: Long = AutoPlace.NO_PLANE_WAIT_MS) {
    private var placedFor: Any? = null
    private var hasPlaced = false
    var overridden = false
        private set

    /** A floor tap or a sign fix set the transform. */
    fun override() { overridden = true }

    fun shouldPlace(
        routeKey: Any?, hasTransform: Boolean, outdoorLeg: Boolean,
        trackingForMs: Long?, floorPlane: Boolean, headingReady: Boolean,
    ): Boolean {
        if (overridden || outdoorLeg || hasTransform || routeKey == null) return false
        if (hasPlaced && placedFor == routeKey) return false
        if (trackingForMs == null || !headingReady) return false
        return floorPlane || trackingForMs >= noPlaneWaitMs
    }

    fun placed(routeKey: Any?) { placedFor = routeKey; hasPlaced = true }
}
