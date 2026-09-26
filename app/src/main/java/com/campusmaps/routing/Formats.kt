package com.campusmaps.routing

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Every number the user sees goes through here, so the formats stay the same on every screen
// (section 13 of the design handoff). Metres and seconds only. Never feet, never "minutes only".
object Formats {

    // ETA as m:ss, rounded to the nearest second: "0:44", "2:14". Unknown: "--:--".
    fun eta(seconds: Double?): String {
        if (seconds == null || seconds.isNaN() || seconds < 0) return "--:--"
        val total = seconds.roundToInt()
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    // Difference to the best route: seconds under a minute ("+47 s"), otherwise m:ss ("+1:39").
    fun etaDifference(seconds: Double): String {
        val total = seconds.roundToInt()
        return if (total < 60) "+$total s" else "+${eta(total.toDouble())}"
    }

    // "in 16 m". Rounded to whole metres, never below 0.
    fun inDistance(metres: Double): String = "in ${metres.coerceAtLeast(0.0).roundToInt()} m"

    fun metres(metres: Double): String = "${metres.roundToInt()} m"

    // "F1" is used on the S2 floor badge and the minimap.
    fun floorShort(floor: Int): String = "F$floor"

    // "Floor 1" is used everywhere else.
    fun floorLong(floor: Int): String = "Floor $floor"

    // "Sat 21:00" for the simulated time chip and error messages.
    fun dayTime(time: LocalDateTime): String {
        val day = time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        val hh = time.hour.toString().padStart(2, '0')
        val mm = time.minute.toString().padStart(2, '0')
        return "$day $hh:$mm"
    }

    fun shortDay(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.US)

    // 1 -> "one", 2 -> "two" ... for "Take the stairs up two floors".
    fun numberWord(n: Int): String = when (abs(n)) {
        1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"
        6 -> "six"; 7 -> "seven"; 8 -> "eight"; 9 -> "nine"; 10 -> "ten"
        else -> abs(n).toString()
    }

    // "84 m · 1:05" for the recording readout on S4.
    fun distanceAndTime(metres: Double, seconds: Double): String = "${metres(metres)} · ${eta(seconds)}"
}
