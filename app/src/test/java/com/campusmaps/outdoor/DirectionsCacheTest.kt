package com.campusmaps.outdoor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionsCacheTest {
    private val start = LatLngPoint(33.7545, -84.3880)
    private fun north(m: Double) = LatLngPoint(start.lat + m / 111_320.0, start.lng)
    private val route = OutdoorRoutes.straight(start, LatLngPoint(33.7560, -84.3880))

    @Test fun firstRequestIsNeeded() {
        assertTrue(DirectionsCache().shouldRequest("E-N", start))
    }

    @Test fun gpsJitterAndShortMovesReuseTheAnswer() {
        val c = DirectionsCache()
        c.store("E-N", start, route)
        assertFalse(c.shouldRequest("E-N", start))
        assertFalse(c.shouldRequest("E-N", north(5.0)))
        assertFalse(c.shouldRequest("E-N", north(8.0)))
        assertSame(route, c.cached())
    }

    @Test fun movingMoreThan25mRequestsAgain() {
        val c = DirectionsCache()
        c.store("E-N", start, route)
        assertTrue(c.shouldRequest("E-N", north(45.0)))
    }

    @Test fun newEntranceRequestsAgain() {
        val c = DirectionsCache()
        c.store("E-N", start, route)
        assertTrue(c.shouldRequest("E-S", start))
    }

    @Test fun aFailureIsCachedToo() {
        val c = DirectionsCache()
        c.store("E-N", start, null)
        assertFalse(c.shouldRequest("E-N", north(3.0)))
        assertNull(c.cached())
    }

    @Test fun snapIsAbout15m() {
        val a = DirectionsCache.snap(start)
        val b = DirectionsCache.snap(north(3.0))
        assertTrue(OutdoorRoutes.distanceM(a, start) <= 11.0)
        assertTrue(OutdoorRoutes.distanceM(a, b) < 16.0) // same cell or the next one
    }
}
