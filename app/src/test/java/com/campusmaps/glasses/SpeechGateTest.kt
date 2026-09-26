package com.campusmaps.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGateTest {
    @Test fun sameStepIsSpokenOncePer25s() {
        val g = SpeechGate()
        // Cycles every 6 s during the lift ride (qa-emuA Q4): spoken at 0 s and again at 30 s only.
        val spoken = (0..6).map { g.shouldSpeak("Take the elevator to floor 6", it * 6_000L) }
        assertEquals(listOf(true, false, false, false, false, true, false), spoken)
    }

    @Test fun changedStepIsSpokenAtOnce() {
        val g = SpeechGate()
        assertTrue(g.shouldSpeak("Take the elevator to floor 6", 0))
        assertTrue(g.shouldSpeak("Room 608 is ahead", 1_000))
        assertFalse(g.shouldSpeak("Room 608 is ahead", 2_000))
        // Going back to an earlier text counts as a change too.
        assertTrue(g.shouldSpeak("Take the elevator to floor 6", 3_000))
    }

    @Test fun tunableInterval() {
        val g = SpeechGate(repeatMs = 10_000)
        assertTrue(g.shouldSpeak("A", 0))
        assertFalse(g.shouldSpeak("A", 9_999))
        assertTrue(g.shouldSpeak("A", 10_000))
    }

    @Test fun resetSpeaksAgain() {
        val g = SpeechGate()
        assertTrue(g.shouldSpeak("A", 0))
        g.reset()
        assertTrue(g.shouldSpeak("A", 100))
    }
}
