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

    @Test fun firstThreeMetresRemoveTheCompassError() {
        val r = YawRefiner(); val t0 = placed(0.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        assertEquals("yaw: compass, unrefined", r.status.debugLine())
        val (t, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 4.0)
        assertEquals(1, res.size)
        assertTrue(res[0].first)
        assertEquals(-20.0, res[0].deltaDeg, 0.5)
        assertEquals(3.0, res[0].travelM, 0.3)
        assertEquals(0.0, t.yawDeg, 0.5)
        // The start node stays under the placement point, and 10 m up the route is now 10 m ahead of the walker.
        val p = t.toWorld(0.0, 0.0); assertEquals(0.0, hypot(p.x, p.z), 1e-9)
        val q = t.toWorld(0.0, 10.0); assertEquals(0.0, q.x, 0.1); assertEquals(-10.0, q.z, 0.1)
        assertTrue(r.status.refined)
        assertEquals("yaw: compass, refined -20 deg", r.status.debugLine())
    }

    @Test fun differenceWrapsAroundSouth() {
        // Route heading south (building -y); true yaw 170, compass says -170 (190): 20 degrees apart across +-180.
        val south = listOf(PathPoint(0.0, 0.0, 1), PathPoint(0.0, -30.0, 1))
        val r = YawRefiner(); val t0 = placed(170.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val truth = BuildingToWorld(Math.toRadians(170.0), 0.0, 0.0, 0.0, 1)
        val d = truth.toWorld(0.0, -1.0)
        val (t, res) = walk(r, t0, south, 0.0, 0.0, Math.toDegrees(BuildingToWorld.yawOf(d.x, d.z)), 3.5)
        assertEquals(1, res.size)
        assertEquals(-20.0, res[0].deltaDeg, 0.5)
        assertEquals(0.0, Math.toDegrees(BuildingToWorld.wrapPi(t.yawRad - truth.yawRad)), 0.5)
    }

    @Test fun correctionIsCappedAt45Degrees() {
        val r = YawRefiner(); val t0 = placed(0.0, -60.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val (t, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 3.5)
        assertEquals(1, res.size)
        assertEquals(60.0, res[0].residualDeg, 0.5)
        assertEquals(45.0, res[0].deltaDeg, 1e-9)
        assertEquals(-15.0, t.yawDeg, 0.5)
        // The total never goes past 45 either: later residuals of 15 can add nothing.
        val (_, more) = walk(r, t, north, 3.5 * sin(Math.PI), 3.5 * cos(Math.PI), trueNorthWorldYaw, 9.0)
        assertTrue(more.all { it.deltaDeg == 0.0 })
        assertEquals(45.0, r.status.correctionDeg, 1e-9)
    }

    @Test fun nothingUnderThreeMetres() {
        val r = YawRefiner(); val t0 = placed(0.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val (_, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 2.9)
        assertTrue(res.isEmpty())
        assertFalse(r.status.refined)
    }

    @Test fun nothingOnACurvedPath() {
        // 2.5 m one way, then a right angle for 2.5 m: 3.5 m apart but the corner is 1.8 m off the line; no straight 3 m.
        val r = YawRefiner(); val t0 = placed(0.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val (t1, a) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 2.5)
        val (_, b) = walk(r, t1, north, 0.0, -2.5, 90.0, 2.5)
        assertTrue(a.isEmpty() && b.isEmpty())
        assertFalse(r.status.refined)
        assertFalse(YawRefiner.straight(listOf(0.0 to 0.0, 0.0 to -2.5, 2.5 to -2.5), 1.0))
        assertTrue(YawRefiner.straight(listOf(0.0 to 0.0, 0.5 to -1.5, 0.0 to -3.0), 1.0))
    }

    @Test fun oneShotThenSlowFilter() {
        val r = YawRefiner(); val t0 = placed(0.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val (t1, first) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 3.2)
        assertEquals(1, first.size)
        // Now the walker's direction is 10 degrees off the (corrected) route: 10% of it per straight 3 m.
        val (_, slow) = walk(r, t1, north, 0.0, -3.2, trueNorthWorldYaw + 10.0, 3.2)
        assertEquals(1, slow.size)
        assertFalse(slow[0].first)
        // (about 10: the window starts where the first measurement ended, up to 0.2 m back on the first line)
        assertEquals(10.0, slow[0].residualDeg, 1.0)
        assertEquals(slow[0].residualDeg * YawRefiner.SLOW_GAIN, slow[0].deltaDeg, 1e-9)
        assertEquals(first[0].deltaDeg + slow[0].deltaDeg, r.status.correctionDeg, 1e-9)
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

    @Test fun floorTapHeadingIsRefinedByWalking() {
        // The phone pointed 10 degrees off the hallway at the tap; the first straight 3 m takes it all out.
        val r = YawRefiner(); val t0 = placed(0.0, 10.0)
        r.startTap(0.0, 0.0, 0.0, 0.0)
        assertEquals("yaw: floor tap", r.status.debugLine())
        assertFalse(r.status.compassPlaced) // the reroute tolerance stays at 6 m
        val (t, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 3.5)
        assertEquals(1, res.size)
        assertEquals(-10.0, res[0].deltaDeg, 0.5)
        assertEquals(0.0, t.yawDeg, 0.5)
        val p = t.toWorld(0.0, 0.0); assertEquals(0.0, hypot(p.x, p.z), 1e-9)
        assertEquals("yaw: floor tap, refined -10 deg", r.status.debugLine())
    }

    @Test fun floorTapCorrectionIsCappedAt15Degrees() {
        // A sideways walk right after a good tap (or a very bad tap) moves the heading at most 15 degrees in total.
        val r = YawRefiner(); val t0 = placed(0.0, 30.0)
        r.startTap(0.0, 0.0, 0.0, 0.0)
        val (t, res) = walk(r, t0, north, 0.0, 0.0, trueNorthWorldYaw, 3.5)
        assertEquals(1, res.size)
        assertEquals(-30.0, res[0].residualDeg, 0.5)
        assertEquals(-YawRefiner.MAX_TAP_CORRECTION_DEG, res[0].deltaDeg, 1e-9)
        assertEquals(15.0, t.yawDeg, 0.5)
        // The total never goes past 15: the remaining 15 degrees can add nothing.
        val (_, more) = walk(r, t, north, 0.0, -3.5, trueNorthWorldYaw, 6.5)
        assertTrue(more.all { it.deltaDeg == 0.0 })
        assertEquals(-15.0, r.status.correctionDeg, 1e-9)
    }

    @Test fun routeTurnInsideTheWindowIsSkipped() {
        // The route turns 90 degrees 1.5 m after the start; walking straight there says nothing about the heading.
        val turn = listOf(PathPoint(0.0, 0.0, 1), PathPoint(0.0, 1.5, 1), PathPoint(20.0, 1.5, 1))
        val r = YawRefiner(); val t0 = placed(0.0, 20.0)
        r.startCompass(0.0, 0.0, 0.0, 0.0)
        val (_, res) = walk(r, t0, turn, 0.0, 0.0, trueNorthWorldYaw, 3.2)
        assertTrue(res.isEmpty())
    }

    @Test fun rotateAboutKeepsThePivot() {
        val t = BuildingToWorld(0.3, 1.0, 0.5, -2.0, 1)
        val r = YawRefiner.rotateAbout(t, Math.toRadians(25.0), 4.0, 7.0)
        val a = t.toWorld(4.0, 7.0); val b = r.toWorld(4.0, 7.0)
        assertEquals(a.x, b.x, 1e-9); assertEquals(a.z, b.z, 1e-9)
        assertEquals(0.3 + Math.toRadians(25.0), r.yawRad, 1e-9)
    }

    @Test fun routePathProjectAndSlice() {
        val l = listOf(PathPoint(0.0, 0.0, 1), PathPoint(0.0, 10.0, 1), PathPoint(10.0, 10.0, 1), PathPoint(10.0, 10.0, 3))
        assertEquals(4.0, RoutePath.project(l, 1, 0.5, 4.0)!!, 1e-9)
        assertEquals(13.0, RoutePath.project(l, 1, 3.0, 11.0)!!, 1e-9)
        assertNull(RoutePath.project(l, 2, 0.0, 0.0))
        val s = RoutePath.slice(l, 8.0, 12.0)!!
        assertEquals(3, s.size)
        assertEquals(PathPoint(0.0, 8.0, 1), s[0]); assertEquals(PathPoint(2.0, 10.0, 1), s[2])
        assertNull(RoutePath.slice(l, 18.0, 25.0))
        assertNotNull(RoutePath.slice(l, 0.0, 20.0))
    }
}
