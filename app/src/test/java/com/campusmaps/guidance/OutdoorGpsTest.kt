package com.campusmaps.guidance

import com.campusmaps.data.campus.TestBuildings
import com.campusmaps.geo.LocationFix
import com.campusmaps.outdoor.LatLngPoint
import com.campusmaps.outdoor.StreetStep
import com.campusmaps.outdoor.StreetSteps
import com.campusmaps.outdoor.StreetTurn
import com.campusmaps.route.CoreRouter
import com.campusmaps.route.FloorChange
import com.campusmaps.route.Formats
import com.campusmaps.route.RoutePlan
import com.campusmaps.shared.WatchStepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class OutdoorGpsTest {

    private val base = (CoreRouter().plan(TestBuildings.cs, "P1", "R-608", LocalDateTime.of(2026, 9, 25, 12, 0), avoidStairs = false) as RoutePlan.Options)
        .options.first { it.method == FloorChange.ELEVATOR }.route
    private val door = LatLngPoint(33.7528343, -84.38760369)
    private val corner = LatLngPoint(33.7540, -84.3870)
    private val route = StreetSteps.apply(base, listOf(
        StreetStep("Head south on Courtland St NE", StreetTurn.STRAIGHT, corner, 300.0),
        StreetStep("Turn left onto Decatur St SE", StreetTurn.LEFT, door, 150.0),
    ), door, "Classroom South", "Library South entrance")
    private val now = 1_000_000L

    // A fix [northM] metres north of [p], [ageMs] old.
    private fun fix(p: LatLngPoint, northM: Double, ageMs: Long = 0) =
        LocationFix(p.lat + northM / 111_195.0, p.lng, 5.0, now - ageMs)

    @Test fun completesUnderTwelveMetres() {
        assertEquals(1, OutdoorGps.nextIndex(route, 0, 0, fix(corner, 11.0), now, now - 5_000, force = false))
        assertEquals(0, OutdoorGps.nextIndex(route, 0, 0, fix(corner, 13.0), now, now - 5_000, force = false))
    }

    @Test fun walkerCannotAdvanceWhileTheFixIsFresh() {
        // The engine (fake walker) says step 3; a fresh (9 s old) fix far from the corner keeps step 0.
        assertEquals(0, OutdoorGps.nextIndex(route, 0, 3, fix(corner, 200.0, ageMs = 9_000), now, now, force = false))
        // The debug Step still moves exactly one step.
        assertEquals(1, OutdoorGps.nextIndex(route, 0, 3, fix(corner, 200.0), now, now, force = true))
    }

    @Test fun staleFixIsIgnored() {
        // 11 m but 11 s old: GPS does not complete it, and the walker's answer stands.
        assertFalse(OutdoorGps.completes(route, 0, fix(corner, 11.0, ageMs = 11_000), now, now - 5_000))
        assertEquals(0, OutdoorGps.nextIndex(route, 0, 0, fix(corner, 11.0, ageMs = 11_000), now, now - 5_000, force = false))
        assertEquals(2, OutdoorGps.nextIndex(route, 0, 2, fix(corner, 11.0, ageMs = 11_000), now, now - 5_000, force = false))
        assertEquals(1, OutdoorGps.nextIndex(route, 0, 0, null, now, now, force = true))
    }

    @Test fun enterStepIsReadBeforeGoingIndoors() {
        // The Enter step (index 2) ends at the same door as the last street step: it stays up 3 s, then the indoor steps.
        assertEquals(2, OutdoorGps.nextIndex(route, 2, 2, fix(door, 5.0), now, now - 1_000, force = false))
        assertEquals(3, OutdoorGps.nextIndex(route, 2, 2, fix(door, 5.0), now, now - 3_000, force = false))
        assertNull(route.steps[3].outdoorEnd)
    }

    @Test fun indoorStepsFollowTheEngine() {
        assertEquals(5, OutdoorGps.nextIndex(route, 4, 5, fix(door, 500.0), now, now, force = false))
    }

    @Test fun watchDistanceEqualsBannerDistanceOutdoors() {
        val step = route.steps[0]
        // GuidanceState.distanceToStepM is this number; the banner and GuidanceEngine.watchStep both read it.
        val d = OutdoorGps.displayDistanceM(step, fix(corner, 120.0), now, alongM = 0.0, engineDistanceM = 15.0)
        assertEquals(120.0, d, 0.5)
        val w = GuidanceEngine.watchStep(step, d, null, showLocked = false)
        assertEquals(WatchStepType.STRAIGHT, w.type)
        assertEquals(Formats.metres(d), w.bigText)
        assertEquals("in ${w.bigText}", Formats.inDistance(d))
        assertEquals(step.text, w.label)
        // Left street turn: the LEFT arrow with the same number.
        val d1 = OutdoorGps.displayDistanceM(route.steps[1], fix(door, 40.0), now, 0.0, 15.0)
        val w1 = GuidanceEngine.watchStep(route.steps[1], d1, null, false)
        assertEquals(WatchStepType.LEFT, w1.type)
        assertEquals("40 m", w1.bigText)
        // A 20 s old fix still shows (no flicker while FusedLocation pauses); a 40 s old one gives the walker's distance.
        assertEquals(d, OutdoorGps.displayDistanceM(step, fix(corner, 120.0, 20_000), now, 0.0, 15.0), 1e-9)
        assertEquals(step.completeAtM, OutdoorGps.displayDistanceM(step, fix(corner, 120.0, 40_000), now, 0.0, 15.0), 1e-9)
    }
}
