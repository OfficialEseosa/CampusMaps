package com.campusmaps.loc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class AutoPlaceTest {
    private fun angleClose(expected: Double, actual: Double, tol: Double = 1e-6) =
        assertEquals(0.0, AutoPlace.diffDeg(expected, actual), tol)

    // ---- compass -> building yaw ----

    @Test fun buildingBearingSubtractsOriginHeadingAndWraps() {
        assertEquals(212.0, AutoPlace.buildingBearingDeg(212.0, 0.0), 1e-9)
        assertEquals(37.0, AutoPlace.buildingBearingDeg(212.0, 175.0), 1e-9)
        assertEquals(350.0, AutoPlace.buildingBearingDeg(10.0, 20.0), 1e-9)   // wraps below 0
        assertEquals(5.0, AutoPlace.buildingBearingDeg(365.0, 360.0), 1e-9)
        assertEquals(0.0, AutoPlace.buildingBearingDeg(20.0, 20.0), 1e-9)
    }

    @Test fun buildingDirIsClockwiseFromPlusY() {
        val (x0, y0) = AutoPlace.buildingDir(0.0); assertEquals(0.0, x0, 1e-9); assertEquals(1.0, y0, 1e-9)
        val (x90, y90) = AutoPlace.buildingDir(90.0); assertEquals(1.0, x90, 1e-9); assertEquals(0.0, y90, 1e-9)
    }

    @Test fun arBearingIsClockwiseFromMinusZ() {
        assertEquals(0.0, AutoPlace.arBearingDeg(0.0, -1.0), 1e-9)
        assertEquals(90.0, AutoPlace.arBearingDeg(1.0, 0.0), 1e-9)
        assertEquals(180.0, AutoPlace.arBearingDeg(0.0, 1.0), 1e-9)
        assertEquals(270.0, AutoPlace.arBearingDeg(-1.0, 0.0), 1e-9)
    }

    /** The phone looks along world (fx, fz) at compass bearing C: the route's building direction at C - H must map onto it. */
    @Test fun transformPutsStartUnderCameraAndTurnsByCompass() {
        for (origin in listOf(0.0, 30.0, 175.0, 359.0)) for (compass in listOf(0.0, 37.0, 212.0, 359.5)) {
            val a = Math.toRadians(123.0); val fx = sin(a); val fz = -cos(a) // ARCore bearing 123
            val t = AutoPlace.transform(2.0, -1.4, 3.0, fx, fz, compass, origin, 5.5, 14.9, 1, 4.0)!!
            val p = t.toWorld(5.5, 14.9)
            assertEquals(2.0, p.x, 1e-9); assertEquals(-1.4, p.y, 1e-9); assertEquals(3.0, p.z, 1e-9)
            assertEquals(1, t.refFloor)
            // The camera direction, read back in the building frame, points at building bearing compass - origin.
            val h = t.buildingHeadingOf(fx, fz) // atan2(dy, dx), 0 = +x, CCW
            val bearing = AutoPlace.wrap360(90.0 - Math.toDegrees(h))
            angleClose(AutoPlace.buildingBearingDeg(compass, origin), bearing, 1e-6)
        }
    }

    @Test fun northFacingPhoneInNorthUpBuildingGivesZeroYaw() {
        // World -Z is north (ARCore bearing = compass), building +y is north: no rotation.
        val t = AutoPlace.transform(0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 1, 3.8)!!
        assertEquals(0.0, t.yawDeg, 1e-9)
    }

    @Test fun zeroHeadingGivesNoTransform() {
        assertNull(AutoPlace.transform(0.0, 0.0, 0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 0.0, 1, 3.8))
    }

    // ---- compass averaging ----

    @Test fun offsetAveragesAcrossNorthWithoutGoingTo180() {
        val o = CompassOffset(windowMs = 1000)
        var t = 0L
        repeat(30) { i -> o.add(t, if (i % 2 == 0) 355.0 else 5.0, 0.0); t += 40 }
        angleClose(0.0, o.offsetDeg()!!, 1e-6)
    }

    @Test fun offsetFollowsPhoneTurningDuringTheWindow() {
        // The phone turns 90 degrees during the window; compass and ARCore turn together, the offset stays 212 - 100.
        val o = CompassOffset(windowMs = 1000)
        for (i in 0..25) { val turn = i * 3.6; o.add(i * 40L, AutoPlace.wrap360(212.0 + turn), AutoPlace.wrap360(100.0 + turn)) }
        angleClose(112.0, o.offsetDeg()!!, 1e-6)
    }

    @Test fun offsetWaitsForAFullSecondAndForASteadyCompass() {
        val o = CompassOffset(windowMs = 1000)
        repeat(10) { o.add(it * 40L, 90.0, 0.0) } // 360 ms
        assertNull(o.offsetDeg())
        val noisy = CompassOffset(windowMs = 1000)
        repeat(30) { noisy.add(it * 40L, if (it % 2 == 0) 0.0 else 180.0, 0.0) }
        assertNull(noisy.offsetDeg())
    }

    // ---- entrance heading ----

    @Test fun entranceUsesDoorHeadingUnlessCompassDisagreesStrongly() {
        assertEquals(90.0, AutoPlace.entranceBearingDeg(null, 90.0), 1e-9)
        assertEquals(90.0, AutoPlace.entranceBearingDeg(130.0, 90.0), 1e-9)
        assertEquals(350.0, AutoPlace.entranceBearingDeg(20.0, 350.0), 1e-9) // 30 degrees across north
        assertEquals(270.0, AutoPlace.entranceBearingDeg(270.0, 90.0), 1e-9) // turned round
    }

    // ---- floor height ----

    @Test fun floorUsesHighestPlaneInRangeBelowCamera() {
        val (y, plane) = AutoPlace.floorY(1.5, listOf(0.1, 1.2, -3.0, 0.0))
        assertTrue(plane); assertEquals(0.1, y, 1e-9) // 1.2 is a table (0.3 m below), -3.0 a floor below
    }

    @Test fun floorFallsBackToCameraHeight() {
        val (y, plane) = AutoPlace.floorY(0.8, emptyList())
        assertFalse(plane); assertEquals(0.8 - 1.4, y, 1e-9)
        val (y2, plane2) = AutoPlace.floorY(0.8, listOf(0.6, -2.0)) // both out of range
        assertFalse(plane2); assertEquals(-0.6, y2, 1e-9)
    }

    // ---- place once ----

    @Test fun gateWaitsForPlaneOrThreeSecondsAndHeading() {
        val g = AutoPlaceGate()
        assertFalse(g.shouldPlace("r", false, false, null, true, true))      // not tracking
        assertFalse(g.shouldPlace("r", false, false, 500, false, true))      // no plane yet, 0.5 s
        assertFalse(g.shouldPlace("r", false, false, 500, true, false))      // compass not steady
        assertTrue(g.shouldPlace("r", false, false, 500, true, true))        // plane seen
        assertTrue(g.shouldPlace("r", false, false, 3000, false, true))      // 3 s without a plane
    }

    @Test fun gatePlacesOncePerRouteAndNeverAfterTapOrSign() {
        val g = AutoPlaceGate()
        assertTrue(g.shouldPlace("r1", false, false, 4000, true, true))
        g.placed("r1")
        assertFalse(g.shouldPlace("r1", false, false, 4000, true, true))    // same route: not twice
        assertFalse(g.shouldPlace("r2", true, false, 4000, true, true))     // new route but a transform exists
        assertTrue(g.shouldPlace("r2", false, false, 4000, true, true))     // new route, transform gone
        g.override()
        assertFalse(g.shouldPlace("r3", false, false, 4000, true, true))    // a tap or sign fix wins for the session
    }

    @Test fun gateNeverPlacesOnTheOutdoorLeg() {
        assertFalse(AutoPlaceGate().shouldPlace("r", false, true, 9000, true, true))
    }
}
