package com.campusmaps.outdoor

import com.campusmaps.data.campus.TestBuildings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class OutdoorRoutesTest {
    private val now = LocalDateTime.of(2026, 9, 26, 14, 0) // Saturday afternoon
    private val test = LatLngPoint(33.7545, -84.3880)     // The emulator fix used in QA, about 300 m from CS

    @Test fun straightLineFallbackUsesHaversineAndWalkingSpeed() {
        val a = LatLngPoint(33.7530, -84.3853)
        val b = LatLngPoint(33.7539, -84.3853) // 0.0009 deg north, about 100 m
        val r = OutdoorRoutes.straight(a, b)
        assertEquals(listOf(a, b), r.points)
        assertEquals(100.0, r.distanceM, 1.0)
        assertEquals(2, r.minutes) // 100 m / 1.3 m/s = 77 s -> 2 min (rounded up)
        assertEquals(RouteSource.STRAIGHT_LINE, r.source)
        assertTrue(r.streetSteps.isEmpty())
    }

    @Test fun walkingMinutesRoundUpAndAtLeastOne() {
        assertEquals(1, OutdoorRoutes.walkingMinutes(0.0))
        assertEquals(1, OutdoorRoutes.walkingMinutes(78.0))
        assertEquals(4, OutdoorRoutes.walkingMinutes(300.0)) // 231 s
    }

    @Test fun planPicksAnEntranceWithCoordinatesNotTheBuildingPin() {
        val plan = OutdoorRoutes.plan(TestBuildings.cs, "R-608", test, now, avoidStairs = false)
        assertNotNull(plan)
        plan!!
        assertFalse(plan.approximate)
        val e = TestBuildings.cs.core.node(plan.entranceId)
        assertEquals(e.lat!!, plan.entrance.lat, 1e-9)
        assertEquals(e.lng!!, plan.entrance.lng, 1e-9)
        assertEquals("Room 608", plan.destinationName)
        assertNotNull(plan.firstIndoorInstruction)
    }

    @Test fun stepListWithoutDirections() {
        val plan = OutdoorRoutes.plan(TestBuildings.cs, "R-608", test, now, avoidStairs = false)!!
        val steps = OutdoorRoutes.sheetSteps(OutdoorRoutes.straight(test, plan.entrance), plan)
        val door = OutdoorRoutes.shortName(plan.entranceName)
        assertEquals("Walk to the $door", steps[0])
        assertEquals("Enter Classroom South at the $door", steps[1])
        assertEquals(plan.firstIndoorInstruction, steps[2])
        assertEquals(3, steps.size)
    }

    @Test fun stepListWithDirectionsStreetSteps() {
        val plan = OutdoorRoutes.plan(TestBuildings.cs, "R-608", test, now, avoidStairs = false)!!
        val route = OutdoorRoute(listOf(test, plan.entrance), 320.0, 5, listOf("Head east on Edgewood Ave", "Turn right"), RouteSource.DIRECTIONS)
        val steps = OutdoorRoutes.sheetSteps(route, plan)
        assertEquals(listOf("Head east on Edgewood Ave", "Turn right"), steps.take(2))
        assertTrue(steps[2].startsWith("Enter Classroom South at the "))
    }

    @Test fun shortNameDropsFloorNote() {
        assertEquals("Library South entrance", OutdoorRoutes.shortName("Library South entrance (floor 2)"))
        assertEquals("Walters main entrance", OutdoorRoutes.shortName("Walters main entrance"))
    }

    @Test fun exploreIsHomeWhenFarOrNoFixButNotInDemoMode() {
        val all = TestBuildings.all
        assertTrue(OutdoorRoutes.exploreIsHome(all, null, demoMode = false))
        assertTrue(OutdoorRoutes.exploreIsHome(all, test, demoMode = false)) // ~300 m from CS
        val atCs = LatLngPoint(TestBuildings.cs.core.origin.lat, TestBuildings.cs.core.origin.lng)
        assertFalse(OutdoorRoutes.exploreIsHome(all, atCs, demoMode = false))
        assertFalse(OutdoorRoutes.exploreIsHome(all, null, demoMode = true))
    }

    @Test fun polylineDecodesGoogleExample() {
        // Google's documented example: (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        val p = OutdoorRoutes.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, p.size)
        assertEquals(38.5, p[0].lat, 1e-6); assertEquals(-120.2, p[0].lng, 1e-6)
        assertEquals(43.252, p[2].lat, 1e-6); assertEquals(-126.453, p[2].lng, 1e-6)
    }

    @Test fun directionsParserFallsBackOnErrorStatus() {
        assertNull(DirectionsClient.parse("""{"status":"REQUEST_DENIED","routes":[]}"""))
        val ok = DirectionsClient.parse(
            """{"status":"OK","routes":[{"overview_polyline":{"points":"_p~iF~ps|U_ulLnnqC"},
               "legs":[{"distance":{"value":260},"steps":[{"html_instructions":"Head <b>west</b> on <b>Gilmer St</b>"}]}]}]}"""
        )!!
        assertEquals(260.0, ok.distanceM, 0.0)
        assertEquals(listOf("Head west on Gilmer St"), ok.streetSteps)
        assertEquals(RouteSource.DIRECTIONS, ok.source)
    }
}
