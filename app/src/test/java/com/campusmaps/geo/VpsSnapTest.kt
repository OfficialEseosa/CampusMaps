package com.campusmaps.geo

import com.campusmaps.data.Geo
import com.campusmaps.data.campus.TestBuildings
import com.campusmaps.data.model.NodeKind
import com.campusmaps.route.CoreRouter
import com.campusmaps.route.FloorChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

// Outdoor leg: the Geospatial (VPS) position replaces FusedLocation when it is good, and standing within 15 m of any
// entrance ends the outdoor leg there (rerouting when it is not the planned door).
class VpsSnapTest {
    private val cs = TestBuildings.cs
    private val wm = cs.core.nodes.first { it.id == "E-WM" }

    // 8 m south-west of the Walters main entrance, replayed by the fake provider.
    private fun near(distM: Double): LatLng {
        val (lat, lng) = Geo.offset(wm.lat!!, wm.lng!!, -distM / Math.sqrt(2.0), -distM / Math.sqrt(2.0))
        return LatLng(lat, lng)
    }

    private fun trackingAt(p: LatLng, acc: Double = 3.0): GeoState {
        val fake = FakeGeospatialProvider(from = p, to = p, localizeSteps = 0, startAccuracyM = acc, endAccuracyM = acc)
        fake.start(); fake.step()
        return fake.state.value
    }

    @Test fun trackingPositionEightMetresFromWaltersSnapsThereAndReroutes() {
        val s = trackingAt(near(8.0))
        assertEquals(EarthTracking.TRACKING, s.tracking)
        val fix = VpsPosition.fixOf(s, 0L)!!
        val snap = VpsPosition.snap(cs.core, fix, plannedEntranceId = "E-LM2")!!
        assertEquals("E-WM", snap.entranceId)
        assertEquals(8.0, snap.distanceM, 0.3)
        assertTrue(snap.reroute)
        assertEquals("VPS snap to E-WM, 8.0 m (planned another door: reroute)", VpsPosition.logLine(snap))
        // The reroute GuidanceController.jumpTo does: best route from that door, same floor-change method.
        val route = CoreRouter().bestRoute(cs, snap.entranceId, "R-608", LocalDateTime.of(2026, 9, 26, 14, 0),
            avoidStairs = false, preferMethod = FloorChange.ELEVATOR)
        assertNotNull(route)
        assertEquals("E-WM", route!!.points.first().node.id)
        assertEquals(NodeKind.ENTRANCE, route.points.first().node.kind)
    }

    @Test fun plannedDoorSnapsWithoutReroute() {
        val snap = VpsPosition.snap(cs.core, VpsPosition.fixOf(trackingAt(near(6.2)), 0L)!!, "E-WM")!!
        assertEquals("E-WM", snap.entranceId)
        assertEquals(false, snap.reroute)
    }

    @Test fun noSnapFartherThan15m() {
        assertNull(VpsPosition.snap(cs.core, VpsPosition.fixOf(trackingAt(near(40.0)), 0L)!!, "E-LM2"))
    }

    @Test fun onlyTrackingUnder10mCounts() {
        assertNull(VpsPosition.fixOf(trackingAt(near(8.0), acc = 12.0), 0L))
        assertNull(VpsPosition.fixOf(GeoState(tracking = EarthTracking.PAUSED, lat = 1.0, lng = 1.0, horizontalAccuracyM = 2.0), 0L))
        assertNotNull(VpsPosition.fixOf(trackingAt(near(8.0), acc = 9.0), 0L))
    }

    @Test fun freshVpsFixHidesFusedLocation() = runBlocking {
        val vps = MutableStateFlow<LocationFix?>(null)
        var now = 10_000L
        val fused = flow {
            emit(LocationFix(1.0, 1.0, 20.0, now))             // no VPS yet: passes
            vps.value = LocationFix(2.0, 2.0, 1.0, now)         // VPS: passes
            emit(LocationFix(3.0, 3.0, 20.0, now))             // hidden by the fresh VPS fix
            now += 5_000
            emit(LocationFix(4.0, 4.0, 20.0, now))             // VPS stale: passes
        }
        val got = withTimeout(5_000) { VpsPosition.preferVps(fused, vps) { now }.take(3).toList() }
        assertEquals(listOf(1.0, 2.0, 4.0), got.map { it.lat }.sorted())
    }
}

class VpsSnapPlannedDoorWinsTest {
    private val cs = com.campusmaps.data.campus.TestBuildings.cs

    /** Walters main (E-WM) and Walters side (E-WS) are close: standing between them, the planned side door wins. */
    @Test fun plannedSideDoorWinsOverNearerMainDoor() {
        val side = cs.core.nodes.first { it.id == "E-WS" }
        val main = cs.core.nodes.first { it.id == "E-WM" }
        // A point 12 m from the side door on the far side from the main door, then nudged toward main.
        val fix = LocationFix(lat = (side.lat!! * 0.4 + main.lat!! * 0.6), lng = (side.lng!! * 0.4 + main.lng!! * 0.6), accuracyM = 3.0, timeMs = 0L)
        val dSide = com.campusmaps.data.Geo.haversineM(fix.lat, fix.lng, side.lat!!, side.lng!!)
        val dMain = com.campusmaps.data.Geo.haversineM(fix.lat, fix.lng, main.lat!!, main.lng!!)
        org.junit.Assume.assumeTrue("doors close enough for the case", dSide <= VpsPosition.SNAP_M && dMain < dSide)
        val snap = VpsPosition.snap(cs.core, fix, plannedEntranceId = "E-WS")!!
        assertEquals("E-WS", snap.entranceId)
        assertEquals(false, snap.reroute)
    }

    @Test fun otherDoorOnlyWhenPlannedIsFarAndItIsWithinTenMetres() {
        val main = cs.core.nodes.first { it.id == "E-WM" }
        val fix = LocationFix(lat = main.lat!!, lng = main.lng!!, accuracyM = 3.0, timeMs = 0L)
        val snap = VpsPosition.snap(cs.core, fix, plannedEntranceId = "E-LM2")!!
        assertEquals("E-WM", snap.entranceId)
        assertTrue(snap.reroute)
    }
}
