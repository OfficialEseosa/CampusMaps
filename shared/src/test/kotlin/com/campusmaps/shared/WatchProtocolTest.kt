package com.campusmaps.shared

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchProtocolTest {

    @Test
    fun encodeThenDecodeGivesTheSameStep() {
        val step = WatchStep(WatchStepType.LEFT, "10 m", "Atrium north")
        assertEquals(step, WatchProtocol.decode(WatchProtocol.encode(step)))
    }

    @Test
    fun everyStepTypeSurvivesTheRoundTrip() {
        for (type in WatchStepType.entries) {
            val step = WatchStep(type, "Room 608", "Arrived")
            assertEquals(step, WatchProtocol.decode(WatchProtocol.encode(step)))
        }
    }

    @Test
    fun garbageDecodesToNull() {
        assertNull(WatchProtocol.decode("hello".toByteArray()))
        assertNull(WatchProtocol.decode("NOPE\u001Fa\u001Fb".toByteArray()))
    }

    @Test
    fun hapticPatternsMatchTheHandoffTable() {
        assertArrayEquals(longArrayOf(80), WatchHaptics.patternFor(WatchStepType.STRAIGHT))
        assertArrayEquals(longArrayOf(80, 80, 80), WatchHaptics.patternFor(WatchStepType.LEFT))
        assertArrayEquals(longArrayOf(80, 80, 80, 80, 80), WatchHaptics.patternFor(WatchStepType.RIGHT))
        assertArrayEquals(longArrayOf(300, 100, 80), WatchHaptics.patternFor(WatchStepType.STAIRS))
        assertArrayEquals(longArrayOf(300, 100, 80), WatchHaptics.patternFor(WatchStepType.STAIRS_DOWN))
        assertArrayEquals(longArrayOf(300, 100, 300), WatchHaptics.patternFor(WatchStepType.ELEVATOR))
        assertArrayEquals(longArrayOf(80, 100, 300), WatchHaptics.patternFor(WatchStepType.DOOR))
        assertArrayEquals(longArrayOf(80, 60, 80, 60, 80, 60, 80), WatchHaptics.patternFor(WatchStepType.LOCKED))
        assertArrayEquals(longArrayOf(600), WatchHaptics.patternFor(WatchStepType.ARRIVED))
        // Android wants a leading 0 ms delay.
        assertArrayEquals(longArrayOf(0, 600), WatchHaptics.waveformTimings(WatchStepType.ARRIVED))
    }
}
