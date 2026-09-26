package com.campusmaps.data

import com.campusmaps.route.Formats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// Either the real time, or a pretend time for demos (so locked doors can be shown at any hour).
sealed interface ClockMode {
    data object Real : ClockMode
    data class Simulated(val day: DayOfWeek, val time: LocalTime) : ClockMode
}

// The one clock the router asks "what time is it?". Core routing never reads the system clock:
// CoreRouter passes now() as Prefs(now = ...).
// When the time is simulated, S1b shows the honest "Routed for Sat 21:00 (simulated)" chip.
// Starts on real time (Raphael's design); the debug overlay's "Sim time" switch turns on Sat 21:00 of the
// event weekend, and its picker (Fri/Sat/Sun/Mon + a time) sets any time. cycle() stays as a shortcut.
class AppClock(initial: ClockMode = ClockMode.Real) {

    private val _mode = MutableStateFlow(initial)
    val mode: StateFlow<ClockMode> = _mode.asStateFlow()

    val isSimulated: Boolean get() = _mode.value is ClockMode.Simulated

    fun now(): LocalDateTime = when (val m = _mode.value) {
        ClockMode.Real -> LocalDateTime.now()
        // A simulated day is a day of the event weekend (Fri 25 to Mon 28 September 2026, docs/20 QA #8).
        is ClockMode.Simulated -> LocalDateTime.of(eventDay(m.day), m.time)
    }

    fun set(mode: ClockMode) {
        _mode.value = mode
    }

    // Debug overlay: step through the presets, ending on real time.
    fun cycle() {
        val all = DEMO_PRESETS + ClockMode.Real
        val index = all.indexOf(_mode.value)
        _mode.value = all[(index + 1) % all.size]
    }

    fun label(): String = when (val m = _mode.value) {
        ClockMode.Real -> "Real time"
        is ClockMode.Simulated -> "${Formats.shortDay(m.day)} ${m.time}"
    }

    // Reset demo: back to the real clock.
    fun reset() {
        _mode.value = ClockMode.Real
    }

    companion object {
        // Saturday of the event weekend; the simulated clock defaults to 21:00 on it (after CSE's Saturday close).
        val EVENT_SATURDAY: LocalDate = LocalDate.of(2026, 9, 26)
        val DEFAULT_SIMULATED: ClockMode.Simulated = ClockMode.Simulated(DayOfWeek.SATURDAY, LocalTime.of(21, 0))

        // The event-weekend date for a day chip: Fri 25, Sat 26, Sun 27, Mon 28 (other weekdays follow on).
        fun eventDay(d: DayOfWeek): LocalDate =
            EVENT_SATURDAY.minusDays(1).plusDays(((d.value - DayOfWeek.FRIDAY.value + 7) % 7).toLong())

        // Sat 21:00 shows the locked Main entrance. Sat 23:00 shows "No route". Tue 10:00 is a normal day.
        val DEMO_PRESETS: List<ClockMode> = listOf(
            ClockMode.Simulated(DayOfWeek.SATURDAY, LocalTime.of(21, 0)),
            ClockMode.Simulated(DayOfWeek.SATURDAY, LocalTime.of(23, 0)),
            ClockMode.Simulated(DayOfWeek.TUESDAY, LocalTime.of(10, 0)),
        )
    }
}
