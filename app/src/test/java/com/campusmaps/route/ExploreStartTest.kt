package com.campusmaps.route

import com.campusmaps.data.campus.CoreBridge
import com.campusmaps.data.campus.TestBuildings
import com.campusmaps.data.model.NodeKind
import com.campusmaps.outdoor.LatLngPoint
import com.campusmaps.outdoor.OutdoorRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

// "Start AR navigation" on Explore: S2 must route from the phone's real fix (the "Your location" node) and go in by the
// same entrance the map drew (qa-phone N2), with the real Classroom South file.
class ExploreStartTest {
    private val now = LocalDateTime.of(2026, 9, 26, 14, 0)
    private val cs = TestBuildings.cs
    private val router = CoreRouter()

    private fun check(fix: LatLngPoint, expectedEntrancePrefix: String): String {
        val map = OutdoorRoutes.plan(cs, "R-608", fix, now, avoidStairs = false)!!
        val withGps = CoreBridge.withGpsStart(cs, fix.lat, fix.lng)
        val plan = router.plan(withGps, CoreBridge.GPS_START_ID, "R-608", now, avoidStairs = false) as RoutePlan.Options
        val option = CoreRouter.optionForEntrance(withGps, plan.options, map.entranceId)
        assertTrue("map picked ${map.entranceName}", map.entranceName.startsWith(expectedEntrancePrefix))
        assertEquals(map.entranceId, plan.options.first().entrance?.id) // core's first S2 option is the map's entrance
        assertEquals(map.entranceId, option.entrance?.id)
        assertEquals(CoreBridge.GPS_START_ID, option.route.points.first().node.id)
        return map.entranceId
    }

    @Test fun fixNearLibrarySouthGoesInByLibrarySouth() {
        assertEquals("E-LM2", check(LatLngPoint(33.75245, -84.38685), "Library South"))
    }

    @Test fun fixNearWaltersGoesInByWalters() {
        check(LatLngPoint(33.75310, -84.38745), "Walters")
    }

    @Test fun qaPhoneFixGetsTheSameEntranceOnMapAndInS2() {
        val fix = LatLngPoint(33.75716, -84.38383) // the S25's desk fix in reports/qa-phone.md
        val map = OutdoorRoutes.plan(cs, "R-608", fix, now, avoidStairs = false)!!
        val withGps = CoreBridge.withGpsStart(cs, fix.lat, fix.lng)
        val plan = router.plan(withGps, CoreBridge.GPS_START_ID, "R-608", now, avoidStairs = false) as RoutePlan.Options
        assertEquals(map.entranceId, CoreRouter.optionForEntrance(withGps, plan.options, map.entranceId).entrance?.id)
        assertEquals(map.entranceId, plan.options.first().entrance?.id)
    }

    @Test fun gpsNodeIsListedFirstAndDrawnNearTheBuilding() {
        val fix = LatLngPoint(33.75716, -84.38383) // about 500 m away
        val b = CoreBridge.withGpsStart(cs, fix.lat, fix.lng)
        val gps = b.node(CoreBridge.GPS_START_ID)
        assertEquals(NodeKind.OUTDOOR, gps.kind)
        assertEquals("Your location", gps.name)
        assertEquals(CoreBridge.GPS_START_ID, b.startIds.first())
        assertEquals(fix.lat, b.outdoorStarts.getValue(CoreBridge.GPS_START_ID).lat, 1e-12)
        val nearestDoor = b.entrances.minOf { it.position.distanceTo(gps.position) }
        assertTrue("drawn $nearestDoor m from a door", nearestDoor <= 30.5)
        // The fixed start points and the plain building are untouched.
        assertTrue(b.startIds.containsAll(cs.startIds))
        assertTrue(CoreBridge.GPS_START_ID !in cs.nodes)
    }

    @Test fun optionForEntranceFallsBackToAlsoVia() {
        val plan = router.plan(cs, "P1", "R-608", now, avoidStairs = false) as RoutePlan.Options
        val first = plan.options.first()
        val via = first.alsoVia.firstOrNull() ?: return
        val viaId = cs.entrances.first { it.name == via }.id
        if (plan.options.any { it.entrance?.id == viaId }) return
        assertEquals(first.id, CoreRouter.optionForEntrance(cs, plan.options, viaId).id)
    }
}
