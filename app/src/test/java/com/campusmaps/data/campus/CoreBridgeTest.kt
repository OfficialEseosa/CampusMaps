package com.campusmaps.data.campus

import com.campusmaps.data.model.NodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoreBridgeTest {

    @Test
    fun yIsFlippedAndEveryCoreNodeIsKept() {
        for (b in TestBuildings.all) {
            for (n in b.core.nodes) {
                val g = b.node(n.id)
                assertEquals(n.x, g.position.x, 1e-9)
                assertEquals(-n.y, g.position.y, 1e-9)
                assertEquals(n.floor, g.floor)
            }
        }
    }

    @Test
    fun outdoorStartsComeFromStartPointsOrTheOrigin() {
        val cs = TestBuildings.cs
        assertEquals(listOf("P1", "P2"), cs.outdoorStarts.keys.toList())
        assertEquals(NodeKind.OUTDOOR, cs.node("P1").kind)
        assertEquals("P1", cs.defaultStartId)
        // Klaus has no surveyed start points: one synthetic point at the origin, but Demo A starts inside at S1.
        val kl = TestBuildings.kl
        assertEquals(listOf(CoreBridge.OUTSIDE_ORIGIN_ID), kl.outdoorStarts.keys.toList())
        assertEquals("S1", kl.defaultStartId)
        assertTrue(kl.startIds.indexOf("S1") < kl.startIds.indexOf("H3"))
        assertEquals(CoreBridge.OUTSIDE_ORIGIN_ID, TestBuildings.cse.defaultStartId)
    }

    @Test
    fun anchorsBecomeSignTextAndDoorsGiveARoomInside() {
        val cs = TestBuildings.cs
        assertEquals("608", cs.node("R-608").signText)
        val kl = TestBuildings.kl
        val room = kl.node("R-1116W")
        assertNotNull(room.roomInside)
        val inside = room.roomInside!!
        // Door faces south, toward someone walking up from the glass staircase (the owner at Klaus: "straight ahead"; was
        // west), so the room lies north of the door: smaller y on the y-down floor plan, same x.
        assertTrue(inside.y < room.position.y)
        assertTrue(abs(inside.x - room.position.x) < 1e-6)
    }

    @Test
    fun demoDestinationsSurvive() {
        assertEquals(listOf("R-1116W", "R-3361"), TestBuildings.kl.demoDestinationIds)
        assertEquals(listOf("R-150", "R-608"), TestBuildings.cs.demoDestinationIds)
        assertEquals(listOf("R-220"), TestBuildings.cse.demoDestinationIds)
    }
}
