package com.campusmaps.route

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class FormatsTest {

    @Test
    fun etaIsMinutesAndSeconds() {
        assertEquals("0:44", Formats.eta(44.0))
        assertEquals("2:14", Formats.eta(134.0))
        assertEquals("2:14", Formats.eta(133.6)) // rounded
        assertEquals("--:--", Formats.eta(null))
        assertEquals("--:--", Formats.eta(Double.NaN))
    }

    @Test
    fun etaDifferenceUsesSecondsUnderAMinute() {
        assertEquals("+47 s", Formats.etaDifference(47.2))
        assertEquals("+1:39", Formats.etaDifference(99.0))
        assertEquals("+1:00", Formats.etaDifference(60.0))
    }

    @Test
    fun distancesAndFloors() {
        assertEquals("in 16 m", Formats.inDistance(15.6))
        assertEquals("in 0 m", Formats.inDistance(-3.0))
        assertEquals("F1", Formats.floorShort(1))
        assertEquals("Floor 6", Formats.floorLong(6))
        assertEquals("84 m · 1:05", Formats.distanceAndTime(84.2, 65.0))
    }

    @Test
    fun dayTimeLooksLikeTheChip() {
        val sat = LocalDateTime.of(2026, 9, 26, 21, 0) // a Saturday
        assertEquals("Sat 21:00", Formats.dayTime(sat))
    }

    @Test
    fun lockedNoticeParsesTheCoreSentence() {
        val n = LockedNotice.fromCore("Heads up: Main entrance is card-only now. Using West entrance instead.")
        assertEquals("Main entrance", n.lockedEntrance)
        assertEquals("West entrance", n.usingEntrance)
        assertEquals("Main entrance is card-only now. Using West entrance instead.", n.text)
        val none = LockedNotice.fromCore("Heads up: Main entrance is card-only now. Taking another way.")
        assertEquals(null, none.usingEntrance)
    }
}
