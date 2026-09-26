package com.campusmaps.geo

import com.campusmaps.data.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffTriggerTest {
    // Walters main entrance, CS.json.
    private val door = LatLng(33.75288157048278, -84.38768797778242)
    private val now = 1_000_000L

    /** A fix [m] metres due south of the door. */
    private fun fixAt(m: Double, time: Long = now): LocationFix {
        val (lat, lng) = Geo.offset(door.lat, door.lng, 0.0, -m)
        return LocationFix(lat, lng, 5.0, time)
    }

    private fun trigger() = HandoffTrigger().apply { reset(door) }

    @Test fun firesOnceBelow40m() {
        val t = trigger()
        assertFalse(t.onFix(fixAt(100.0), now))
        assertFalse(t.onFix(fixAt(45.0), now))
        assertTrue(t.onFix(fixAt(39.0), now))
        assertFalse(t.onFix(fixAt(30.0), now))
        assertFalse(t.onFix(fixAt(10.0), now))
        assertEquals(10.0, t.lastDistanceM!!, 0.1)
    }

    @Test fun jitterBetween40And60DoesNotRefire() {
        val t = trigger()
        assertTrue(t.onFix(fixAt(35.0), now))
        assertFalse(t.onFix(fixAt(55.0), now))
        assertFalse(t.onFix(fixAt(35.0), now))
    }

    @Test fun rearmsAbove60m() {
        val t = trigger()
        assertTrue(t.onFix(fixAt(35.0), now))
        assertFalse(t.onFix(fixAt(65.0), now))
        assertTrue(t.armed)
        assertTrue(t.onFix(fixAt(38.0), now))
    }

    @Test fun newRouteRearms() {
        val t = trigger()
        assertTrue(t.onFix(fixAt(20.0), now))
        t.reset(door)
        assertTrue(t.onFix(fixAt(20.0), now))
    }

    @Test fun ignoresStaleFixes() {
        val t = trigger()
        assertFalse(t.onFix(fixAt(20.0, time = now - 10_001), now))
        assertNull(t.lastDistanceM)
        assertTrue(t.armed)
        // Exactly 10 s old is still fresh.
        assertTrue(t.onFix(fixAt(20.0, time = now - 10_000), now))
    }

    @Test fun noEntranceNeverFires() {
        val t = HandoffTrigger()
        assertFalse(t.onFix(fixAt(1.0), now))
    }

    @Test fun manualButtonConsumesTheTrigger() {
        val m = HandoffStateMachine().apply { newRoute(door) }
        assertEquals(HandoffPhase.ANIMATING, m.startHandoff())
        assertEquals(HandoffPhase.AR, m.animationDone())
        m.backToMap()
        // Walking in after the manual hand-off does not pop the card.
        assertEquals(HandoffPhase.MAP, m.onFix(fixAt(20.0), now))
    }

    @Test fun stateMachineCardThenAnimationThenAr() {
        val m = HandoffStateMachine().apply { newRoute(door) }
        assertEquals(HandoffPhase.MAP, m.onFix(fixAt(80.0), now))
        assertEquals(HandoffPhase.CARD, m.onFix(fixAt(39.0), now))
        assertEquals(HandoffPhase.ANIMATING, m.startHandoff())
        assertEquals(HandoffPhase.ANIMATING, m.startHandoff())
        assertEquals(HandoffPhase.AR, m.animationDone())
        m.newRoute(door)
        assertEquals(HandoffPhase.MAP, m.phase)
        assertEquals(HandoffPhase.CARD, m.onFix(fixAt(39.0), now))
    }
}
