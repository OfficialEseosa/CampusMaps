package com.campusmaps.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeenTrackerTest {
    @Test fun nothingSeenYetStaysNull() {
        val t = SeenTracker()
        assertNull(t.onBurst(null))
        assertNull(t.onBurst(null))
        assertNull(t.onBurst(null))
    }

    @Test fun oneMissKeepsTheSignTwoMissesSayNothingNew() {
        val t = SeenTracker()
        assertEquals("LIBRARY SOUTH", t.onBurst("LIBRARY SOUTH"))
        assertEquals("LIBRARY SOUTH", t.onBurst(null))
        assertEquals(SeenTracker.NOTHING_NEW, t.onBurst(null))
        assertEquals(SeenTracker.NOTHING_NEW, t.onBurst(null))
    }

    @Test fun aMatchResetsTheMissCount() {
        val t = SeenTracker()
        t.onBurst("LIBRARY SOUTH")
        t.onBurst(null)
        assertEquals("LIBRARY SOUTH", t.onBurst("LIBRARY SOUTH"))
        assertEquals("LIBRARY SOUTH", t.onBurst(null))
        assertEquals("608", t.onBurst("608"))
    }

    @Test fun resetClears() {
        val t = SeenTracker()
        t.onBurst("608")
        t.reset()
        assertNull(t.text)
    }
}
