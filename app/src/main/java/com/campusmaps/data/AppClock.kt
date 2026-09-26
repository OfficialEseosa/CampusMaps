package com.campusmaps.data

import com.campusmaps.routing.Formats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

// Either the real time, or a pretend time for demos (so locked doors can be shown at any hour).
sealed interface ClockMode {
    data object Real : ClockMode
    data class Simulated(val day: DayOfWeek, val time: LocalTime) : ClockMode
}

// The one clock the router asks "what time is it?".
// When the time is simulated, S1b shows the honest "Routed for Sat 21:00 (simulated)" chip.
class AppClock(initial: ClockMode = DEMO_PRESETS.first()) {

    private val _mode = MutableStateFlow(initial)
    val mode: StateFlow<ClockMode> = _mode.asStateFlow()

    val isSimulated: Boolean get() = _mode.value is ClockMode.Simulated

    fun now(): LocalDateTime = when (val m = _mode.value) {
        ClockMode.Real -> LocalDateTime.now()
        is ClockMode.Simulated -> LocalDate.now().with(TemporalAdjusters.nextOrSame(m.day)).atTime(m.time)
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

    fun reset() {
        _mode.value = DEMO_PRESETS.first()
    }

    companion object {
        // Sat 21:00 shows the locked Main entrance. Sat 23:00 shows "No route". Tue 10:00 is a normal day.
        val DEMO_PRESETS: List<ClockMode> = listOf(
            ClockMode.Simulated(DayOfWeek.SATURDAY, LocalTime.of(21, 0)),
            ClockMode.Simulated(DayOfWeek.SATURDAY, LocalTime.of(23, 0)),
            ClockMode.Simulated(DayOfWeek.TUESDAY, LocalTime.of(10, 0)),
        )
    }
}
