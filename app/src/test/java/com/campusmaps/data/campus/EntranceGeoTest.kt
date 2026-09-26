package com.campusmaps.data.campus

import com.campusmaps.routing.Prefs
import com.campusmaps.routing.Router
import com.campusmaps.routing.Start
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class EntranceGeoTest {
    private val saturday2pm = Prefs(now = LocalDateTime.of(2026, 9, 26, 14, 0))

    @Test
    fun csP1To608EndsTheOutdoorLegAtLibrarySouth() {
        val cs = TestBuildings.cs
        val p1 = cs.outdoorStarts.getValue("P1")
        val option = Router.route(cs.core, Start.Outside(p1.lat, p1.lng), "R-608", saturday2pm).first()
        val geo = CoreBridge.entranceGeo(cs, option)
        assertNotNull(geo)
        geo!!
        assertEquals("E-LM2", geo.nodeId)
        assertEquals("Library South entrance (floor 2)", geo.name)
        assertEquals(2, geo.floor)
        // Survey CS-20260925-1238 obs #19: fix and facing out 16.3 deg, so you walk in heading 196.3 (south-south-west).
        assertEquals(33.75255063, geo.lat, 1e-7)
        assertEquals(-84.38702004, geo.lng, 1e-7)
        assertEquals(16.3, geo.facingOutDeg!!, 1e-9)
        assertEquals(196.3, geo.headingDeg!!, 1e-9)
        assertFalse(geo.estimated)
        // Same answer by the card's entrance name, and on the drawing node.
        assertEquals(geo, CoreBridge.entranceGeo(cs, option.entranceName!!))
        val node = cs.node("E-LM2")
        assertEquals(geo.lat, node.lat!!, 0.0); assertEquals(geo.lng, node.lng!!, 0.0); assertEquals(geo.headingDeg!!, node.headingDeg!!, 0.0)
    }

    @Test
    fun everyOutdoorEntranceInAllThreeFilesHasGeo() {
        for (b in TestBuildings.all) for (e in b.entrances) {
            val geo = CoreBridge.entranceGeo(b, e.id)
            assertNotNull("${b.id} ${e.id}", geo)
            assertNotNull("${b.id} ${e.id}", geo!!.headingDeg)
        }
        // Guesses stay flagged: every Klaus entrance is estimated until the survey.
        assertEquals(true, TestBuildings.kl.entrances.all { CoreBridge.entranceGeo(TestBuildings.kl, it.id)!!.estimated })
    }

    @Test
    fun nonEntrancesAndInsideStartsGiveNull() {
        val cs = TestBuildings.cs
        assertNull(CoreBridge.entranceGeo(cs, "H4"))
        assertNull(CoreBridge.entranceGeo(cs, "nowhere"))
        assertNull(cs.node("H4").lat)
        val inside = Router.route(cs.core, Start.AtNode("ST-LS-2"), "R-608", saturday2pm).first()
        assertNull(CoreBridge.entranceGeo(cs, inside))
    }
}
