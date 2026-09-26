package com.campusmaps.ui

import com.campusmaps.data.campus.CoreBridge
import com.campusmaps.data.campus.TestBuildings
import com.campusmaps.geo.LocationFix
import com.campusmaps.outdoor.LatLngPoint
import com.campusmaps.outdoor.OutdoorRoutes
import com.campusmaps.route.CoreRouter
import com.campusmaps.route.RoutePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDateTime

// S1 opened directly: a fresh fix away from the building starts from "Your location"; at the building, in demo mode, with a
// stale or poor fix, or after a pick by hand, S1 keeps what it does today.
class GpsStartTest {
    private val cs = TestBuildings.cs
    private val kl = TestBuildings.kl
    private val now = 1_000_000_000L
    private fun fix(lat: Double, lng: Double, acc: Double = 8.0, ageMs: Long = 2_000L) = LocationFix(lat, lng, acc, now - ageMs)

    private val far = fix(33.75558, -84.38769)           // about 300 m north of Classroom South
    private val librarySouth = fix(33.75255, -84.38702)  // at the Library South entrance (E-LM2)

    @Test fun freshFixAwayFromTheBuildingStartsFromYourLocation() {
        val sel = GpsStart.apply(Selection(), cs, far, now, demoMode = false)
        assertEquals(CoreBridge.GPS_START_ID, sel.startId)
        assertEquals(far.lat, sel.gps!!.lat, 1e-12)
        assertEquals(8.0, sel.gpsAutoAccuracyM!!, 0.0)
        assertEquals("From your location (GPS, 8 m)", GpsStart.hint(sel.gpsAutoAccuracyM!!))
    }

    @Test fun atAnEntranceKeepsTheDefaultStart() {
        assertEquals(Selection(), GpsStart.apply(Selection(), cs, librarySouth, now, demoMode = false))
        assertEquals("P1", cs.defaultStartId)
        // 20 m from the door is still "at the building".
        assertNull(GpsStart.decide(cs, fix(33.75255 + 20.0 / 111_195, -84.38702), now, demoMode = false))
    }

    @Test fun demoModeNeverUsesGps() {
        assertEquals(Selection(), GpsStart.apply(Selection(), cs, far, now, demoMode = true))
        assertEquals(Selection(), GpsStart.apply(Selection(), kl, fix(33.7800, -84.3965), now, demoMode = true))
    }

    @Test fun staleOrPoorOrMissingFixKeepsTheDefault() {
        assertNull(GpsStart.decide(cs, fix(far.lat, far.lng, ageMs = 31_000L), now, false))
        assertNull(GpsStart.decide(cs, fix(far.lat, far.lng, acc = 50.0), now, false))
        assertNull(GpsStart.decide(cs, null, now, false))
    }

    @Test fun manualPickWins() {
        val manual = Selection(startId = "P2")
        assertSame(manual, GpsStart.apply(manual, cs, far, now, demoMode = false))
        // Picking "Your location" by hand is also kept, even when a later fix is at the building.
        val pickedGps = GpsStart.apply(Selection(), cs, far, now, false).copy(gpsAutoAccuracyM = null)
        assertSame(pickedGps, GpsStart.apply(pickedGps, cs, librarySouth, now, false))
    }

    @Test fun anAutomaticStartFollowsTheFixAndGoesBackToTheDefaultAtTheBuilding() {
        val auto = GpsStart.apply(Selection(destinationId = "R-608"), cs, far, now, false)
        val moved = GpsStart.apply(auto, cs, fix(33.75600, -84.38769, acc = 12.0), now, false)
        assertEquals(33.75600, moved.gps!!.lat, 1e-12)
        assertSame(moved, GpsStart.apply(moved, cs, fix(33.75601, -84.38769, acc = 12.0), now, false)) // 1 m: no churn
        val back = GpsStart.apply(moved, cs, librarySouth, now, false)
        assertEquals(Selection(destinationId = "R-608"), back)
    }

    @Test fun gpsStartRoutesToTheEntranceTheExploreMapDraws() {
        val t = LocalDateTime.of(2026, 9, 26, 14, 0)
        val sel = GpsStart.apply(Selection(), cs, far, now, false)
        val b = ExploreStart.building(cs, sel.gps)
        val map = OutdoorRoutes.plan(cs, "R-608", LatLngPoint(far.lat, far.lng), t, avoidStairs = false)!!
        val plan = CoreRouter().plan(b, sel.startId!!, "R-608", t, avoidStairs = false) as RoutePlan.Options
        assertNotNull(plan.options.first().entrance)
        assertEquals(map.entranceId, plan.options.first().entrance?.id)
    }
}
