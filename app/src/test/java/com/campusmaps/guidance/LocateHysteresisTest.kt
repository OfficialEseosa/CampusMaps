package com.campusmaps.guidance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocateHysteresisTest {
    @Test fun showsAfterTwoSecondsLowAndHidesAfterOneSecondGood() {
        val h = LocateHysteresis()
        assertFalse(h.update(true, 0))
        assertFalse(h.update(true, 1_999))
        assertTrue(h.update(true, 2_000))
        assertTrue(h.update(false, 2_100))
        assertTrue(h.update(false, 3_099))
        assertFalse(h.update(false, 3_100))
    }

    @Test fun flappingAroundTheThresholdNeverBlinks() {
        val h = LocateHysteresis()
        // Low and good alternating every 200 ms (the desk case): never shown.
        for (t in 0L..10_000L step 200) assertFalse(h.update((t / 200) % 2 == 0L, t))
        // Once shown, short good moments do not hide it.
        for (t in 20_000L..22_000L step 100) h.update(true, t)
        for (t in 22_100L..30_000L step 300) assertTrue(h.update((t / 300) % 2 == 0L, t))
    }
}
