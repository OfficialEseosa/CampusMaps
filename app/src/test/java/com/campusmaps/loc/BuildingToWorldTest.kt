package com.campusmaps.loc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class BuildingToWorldTest {
    private val eps = 1e-9

    private fun assertVec(expected: Vec3, actual: Vec3, tol: Double = 1e-9) {
        assertEquals("x", expected.x, actual.x, tol)
        assertEquals("y", expected.y, actual.y, tol)
        assertEquals("z", expected.z, actual.z, tol)
    }

    @Test fun identityMapsNorthToMinusZ() {
        val t = BuildingToWorld(0.0, 0.0, 0.0, 0.0, refFloor = 1)
        assertVec(Vec3(0.0, 0.0, -5.0), t.toWorld(0.0, 5.0)) // north
        assertVec(Vec3(3.0, 0.0, 0.0), t.toWorld(3.0, 0.0))  // east
    }

    @Test fun tappedNodeLandsOnTapPoint() {
        val hit = Vec3(1.2, -1.4, -0.8)
        // Node at (10, 20); the route's next edge points east; the phone faces world -Z.
        val t = BuildingToWorld.fromCorrespondence(hit, 0.0, -1.0, 10.0, 20.0, 1.0, 0.0, floor = 1)!!
        assertVec(hit, t.toWorld(10.0, 20.0, 1))
    }

    @Test fun edgeDirectionFollowsPhoneHeading() {
        val hit = Vec3(0.5, -1.3, 2.0)
        for (deg in listOf(0.0, 37.0, 90.0, 181.0, -120.0)) {
            val a = Math.toRadians(deg)
            val wdx = sin(a); val wdz = cos(a)
            // Edge from (4,4) heading north-east.
            val t = BuildingToWorld.fromCorrespondence(hit, wdx, wdz, 4.0, 4.0, 1.0, 1.0, floor = 2)!!
            val p0 = t.toWorld(4.0, 4.0, 2); val p1 = t.toWorld(5.0, 5.0, 2)
            val dx = p1.x - p0.x; val dz = p1.z - p0.z; val len = hypot(dx, dz)
            assertEquals("len at $deg", hypot(1.0, 1.0), len, 1e-9)
            assertEquals("dir x at $deg", wdx, dx / len, 1e-9)
            assertEquals("dir z at $deg", wdz, dz / len, 1e-9)
            assertEquals("stays on the floor", hit.y, p1.y, eps)
        }
    }

    @Test fun roundTripBuildingWorldBuilding() {
        val t = BuildingToWorld(Math.toRadians(73.0), 4.0, -1.5, -9.0, refFloor = 1)
        for ((x, y) in listOf(0.0 to 0.0, 12.5 to -3.0, -7.0 to 44.0)) {
            val (bx, by) = t.toBuilding(t.toWorld(x, y))
            assertEquals(x, bx, 1e-9); assertEquals(y, by, 1e-9)
        }
    }

    @Test fun otherFloorsOffsetByFloorHeight() {
        val t = BuildingToWorld(0.3, 0.0, -1.4, 0.0, refFloor = 1, floorHeightM = 4.0)
        assertEquals(-1.4 + 8.0, t.toWorld(1.0, 1.0, floor = 3).y, eps)
        assertEquals(-1.4 + 1.5, t.toWorld(1.0, 1.0, floor = 1, heightM = 1.5).y, eps)
    }

    @Test fun buildingHeadingOfInvertsTheYaw() {
        val t = BuildingToWorld(Math.toRadians(-50.0), 0.0, 0.0, 0.0, refFloor = 1)
        val p0 = t.toWorld(0.0, 0.0); val p1 = t.toWorld(0.0, 1.0) // due north in the building
        assertEquals(PI / 2, t.buildingHeadingOf(p1.x - p0.x, p1.z - p0.z), 1e-9)
    }

    @Test fun lerpTakesTheShortWayRound() {
        val a = BuildingToWorld(Math.toRadians(170.0), 0.0, 0.0, 0.0, 1)
        val b = BuildingToWorld(Math.toRadians(-170.0), 2.0, 0.0, 0.0, 1)
        val mid = a.lerp(b, 0.5)
        assertEquals(180.0, kotlin.math.abs(mid.yawDeg), 1e-9)
        assertEquals(1.0, mid.tx, eps)
    }

    @Test fun degenerateDirectionsGiveNull() {
        assertNull(BuildingToWorld.fromCorrespondence(Vec3(0.0, 0.0, 0.0), 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 1))
        assertNull(BuildingToWorld.fromCorrespondence(Vec3(0.0, 0.0, 0.0), 1.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1))
        assertNotNull(BuildingToWorld.fromCorrespondence(Vec3(0.0, 0.0, 0.0), 1.0, 0.0, 1.0, 1.0, 0.0, 1.0, 1))
    }
}
