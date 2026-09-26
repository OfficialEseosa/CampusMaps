package com.campusmaps.routing

import com.campusmaps.data.model.CardOnlyWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

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
    fun templatesMatchTheHandoff() {
        assertEquals("Turn left at Atrium north", Instructions.turnAt(TurnDirection.LEFT, "Atrium north"))
        assertEquals("Go through Atlantic Drive entrance, doors on the left", Instructions.goThrough("Atlantic Drive entrance", "doors on the left"))
        assertEquals("Take the stairs down two floors", Instructions.stairs(-2))
        assertEquals("Take the elevator to floor 2", Instructions.elevator(2))
        assertEquals("You have arrived at Room 608", Instructions.arrivedAt("Room 608"))
        assertEquals("Main entrance is card-only now. Taking another way.", Instructions.lockedNotice("Main entrance", null))
    }

    @Test
    fun cardOnlyWindowsWrapPastMidnight() {
        val window = CardOnlyWindow(setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(7, 0))
        val friLate = LocalDateTime.of(2026, 9, 25, 23, 0) // Friday
        val satEarly = LocalDateTime.of(2026, 9, 26, 6, 0) // Saturday morning, still Friday's window
        val satLate = LocalDateTime.of(2026, 9, 26, 23, 0)
        assertTrue(window.contains(friLate))
        assertTrue(window.contains(satEarly))
        assertFalse(window.contains(satLate))
    }
}
