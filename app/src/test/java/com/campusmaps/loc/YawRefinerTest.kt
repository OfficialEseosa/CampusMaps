package com.campusmaps.loc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class YawRefinerTest {
    // A straight route 30 m along building +y (north) on floor 1, starting at the placement node (0, 0).
    private val north = listOf(PathPoint(0.0, 0.0, 1), PathPoint(0.0, 30.0, 1))

    /** The compass placement: pivot (0, 0) at the world origin, building yaw off by [errDeg] from the truth [trueDeg]. */
    private fun placed(trueDeg: Double, errDeg: Double) = BuildingToWorld(Math.toRadians(trueDeg + errDeg), 0.0, 0.0, 0.0, 1)

    /** Walks from ([x0], [z0]) in world yaw [yawDeg] (yawOf convention: atan2(x, z)) for [m] metres, 10 cm per sample. */
    private fun walk(r: YawRefiner, t0: BuildingToWorld, route: List<PathPoint>, x0: Double, z0: Double, yawDeg: Double, m: Double):
        Pair<BuildingToWorld, List<YawRefiner.Result>> {
        var t = t0; val out = mutableListOf<YawRefiner.Result>()
        val y = Math.toRadians(yawDeg)
        var s = 0.1
        while (s <= m + 1e-9) {
            r.onCamera(x0 + s * sin(y), z0 + s * cos(y), t, route, 1)?.let { t = it.transform; out += it }
            s += 0.1
        }
        return t to out
    }

    /** World yaw (yawOf) of building +y under the true yaw 0: north is world -Z, yawOf = 180. */
    private val trueNorthWorldYaw = 180.0

    @Test fun compassErrorIsRemovedGraduallyNeverInOneJump() {
        // 20 degree compass error; the first 2.5 m are the doorway and count for nothing; then each straight window
        // takes GAIN of the residual, so after 15 m the route is within 3 degrees and no single step exceeded GAIN * 20.
        val r = YawRefiner(); val t0 = placed(0.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        assertEquals("yaw: compass, unrefined", r.status.debugLine())
        val (t, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 24.0)
        assertTrue(res.size >= 3)
        assertTrue(res.none { kotlin.math.abs(it.deltaDeg) > YawRefiner.GAIN * 20.0 + 0.5 })
        assertTrue(res.first().travelM >= YawRefiner.DOOR_SKIP_M + YawRefiner.MIN_TRAVEL_M - 0.3)
        assertEquals(0.0, t.yawDeg, 3.0)
        val p = t.toWorld(0.0, 0.0); assertEquals(0.0, hypot(p.x, p.z), 1e-9) // the start node stays put
        assertTrue(r.status.refined)
    }

    @Test fun differenceWrapsAroundSouth() {
        val south = listOf(PathPoint(0.0, 0.0, 1), PathPoint(0.0, -30.0, 1))
        val r = YawRefiner(); val t0 = placed(0.0, -20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val (t, res) = walk(r, t0, south, 0.0, 0.0, trueNorthWorldYaw + 180.0, 24.0)
        assertTrue(res.isNotEmpty())
        assertEquals(0.0, t.yawDeg, 3.0)
    }

    @Test fun correctionIsCappedAt25Degrees() {
        val r = YawRefiner(); val t0 = placed(0.0, 50.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val (t, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 40.0)
        assertTrue(res.isNotEmpty())
        assertEquals(-YawRefiner.MAX_CORRECTION_DEG, r.status.correctionDeg, 1e-6)
        assertEquals(25.0, t.yawDeg, 0.5)
    }

    @Test fun aDetourIsIgnored() {
        // Walking 70 degrees off the route is a detour, not a heading error: nothing moves.
        val r = YawRefiner(); val t0 = placed(0.0, 0.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val (t, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw + 70.0, 12.0)
        assertTrue(res.isEmpty())
        assertEquals(0.0, t.yawDeg, 1e-9)
    }

    @Test fun nothingInTheDoorway() {
        val r = YawRefiner(); val t0 = placed(0.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val (_, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, YawRefiner.DOOR_SKIP_M + YawRefiner.MIN_TRAVEL_M - 0.5)
        assertTrue(res.isEmpty())
    }

    @Test fun nothingOnACurvedPath() {
        val r = YawRefiner(); val t0 = placed(0.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        // A zigzag of 2 m legs (north, east, north, east ...): no 3 m window is straight, so nothing is measured.
        var any = false; var x = 0.0; var z = 0.0
        repeat(6) { leg ->
            val dx = if (leg % 2 == 0) 0.0 else 0.1; val dz = if (leg % 2 == 0) -0.1 else 0.0
            repeat(20) { x += dx; z += dz; if (r.onCamera(x, z, t0, north, 1) != null) any = true }
        }
        assertFalse(any)
    }

    @Test fun signFixStopsRefining() {
        val r = YawRefiner(); val t0 = placed(0.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        r.stop(YawSource.SIGN)
        val (_, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 5.0)
        assertTrue(res.isEmpty())
        assertFalse(r.status.compassPlaced)
        assertEquals("yaw: sign fix", r.status.debugLine())
        r.startTap(0.0, 0.0, 0.0, 0.0); r.stop(YawSource.SIGN)
        assertTrue(walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 5.0).second.isEmpty())
    }

    @Test fun floorTapHeadingIsRefinedByWalkingGradually() {
        val r = YawRefiner(); val t0 = placed(0.0, 10.0)
        r.startTap(0.0, 0.0, 0.0, 0.0)
        assertEquals("yaw: floor tap", r.status.debugLine())
        assertFalse(r.status.compassPlaced)
        val (t, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 15.0)
        assertTrue(res.isNotEmpty())
        assertEquals(0.0, t.yawDeg, 3.0)
        val p = t.toWorld(0.0, 0.0); assertEquals(0.0, hypot(p.x, p.z), 1e-9)
    }

    @Test fun floorTapCorrectionIsCappedAt15Degrees() {
        val r = YawRefiner(); val t0 = placed(0.0, 30.0)
        r.startTap(0.0, 0.0, 0.0, 0.0)
        val (t, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 40.0)
        assertTrue(res.isNotEmpty())
        assertEquals(-YawRefiner.MAX_TAP_CORRECTION_DEG, r.status.correctionDeg, 1e-6)
        assertEquals(15.0, t.yawDeg, 0.5)
    }
}
