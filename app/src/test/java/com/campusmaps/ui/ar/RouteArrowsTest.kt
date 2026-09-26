package com.campusmaps.ui.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteArrowsTest {
    // An L: 6 m north, then 3 m east, then an elevator to floor 2.
    private val pts = listOf(
        RoutePoint(0.0, 0.0, 1), RoutePoint(0.0, 6.0, 1), RoutePoint(3.0, 6.0, 1), RoutePoint(3.0, 6.0, 2), RoutePoint(3.0, 10.0, 2),
    )

    @Test fun chainSpacingCarriesAcrossTheCorner() {
        val a = RouteArrows.chain(pts, 1, spacing = 1.5)
        // 9 m of floor-1 path, first arrow at 0.75 m, every 1.5 m: 0.75, 2.25, ..., 8.25 -> 6 arrows.
        assertEquals(6, a.size)
        assertEquals(0.75, a[0].y, 1e-9)
        assertEquals(0.0, a[0].yawDeg, 1e-9) // north = local -Z = yaw 0
        val east = a.last()
        assertEquals(2.25, east.x, 1e-9); assertEquals(6.0, east.y, 1e-9)
        assertEquals(-90.0, east.yawDeg, 1e-9)
        assertTrue(RouteArrows.chain(pts, 2).all { it.x == 3.0 })
    }

    @Test fun defaultSpacingIsLiveViewSize() {
        // 2.5 m: 1.25, 3.75, then 6.25 (0.25 m past the corner) and 8.75 -> 4 arrows on the 9 m of floor 1.
        val a = RouteArrows.chain(pts, 1)
        assertEquals(2.5, RouteArrows.SPACING_M, 1e-9)
        assertEquals(4, a.size)
        assertEquals(1.25, a[0].y, 1e-9)
        assertEquals(0.25, a[2].x, 1e-9); assertEquals(6.0, a[2].y, 1e-9)
    }

    @Test fun clearRadiusDropsArrowsAtTheTurn() {
        val a = RouteArrows.chain(pts, 1, clearAround = 0.0 to 6.0, clearRadius = 1.0)
        assertTrue(a.none { kotlin.math.hypot(it.x, it.y - 6.0) < 1.0 })
    }

    @Test fun placementUsesNextEdgeOrPreviousAtTheEnd() {
        val p0 = RouteArrows.placementAt(pts, 0, "A")!!
        assertEquals(0.0, p0.dirX, 1e-9); assertEquals(1.0, p0.dirY, 1e-9)
        val last = RouteArrows.placementAt(pts, 4, "E")!!
        assertEquals(1.0, last.dirY, 1e-9) // previous edge (3,6)->(3,10), same direction
        assertNull(RouteArrows.arrowAt(pts, 2)) // floor change
    }
}
