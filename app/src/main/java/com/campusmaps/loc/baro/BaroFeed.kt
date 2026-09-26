package com.campusmaps.loc.baro

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the debug card shows about the barometer. */
data class BaroStatus(
    val pressureHpa: Double?,
    val refFloor: Int,
    val refPressureHpa: Double?,
    val floor: Int,
    val deltaHpa: Double?,
    val calibrating: Boolean,
    val gateOpen: Boolean,
) {
    fun debugLine(): String {
        val p = pressureHpa?.let { "%.2f hPa".format(it) } ?: "no reading yet"
        if (calibrating) return "baro: $p, calibrating at floor $refFloor"
        val ref = refPressureHpa?.let { "%.2f".format(it) } ?: "-"
        val d = deltaHpa?.let { "%+.2f".format(it) } ?: "-"
        return "baro: $p, ref floor $refFloor @ $ref, est floor $floor, delta $d${if (gateOpen) " (ride)" else ""}"
    }
}

/**
 * The link between the app (which has a Context) and each guidance session's [BarometerFloorTracker]: CampusMapsApp
 * installs the phone's barometer once, GuidanceController asks for a tracker, the debug card reads [status].
 */
object BaroFeed {
    @Volatile private var source: PressureSource? = null
    @Volatile private var clock: () -> Long = { SystemClock.elapsedRealtime() }

    private val _status = MutableStateFlow<BaroStatus?>(null)
    /** Null while no guidance session uses the barometer. */
    val status: StateFlow<BaroStatus?> = _status.asStateFlow()

    fun install(context: Context) {
        if (source == null) source = SensorPressureSource(context.applicationContext)
    }

    /** For tests and the emulator: any source and clock. */
    fun install(s: PressureSource, now: () -> Long) {
        source = s
        clock = now
    }

    /** A tracker for a new session, or null when the device has no barometer (the simulator's timed rides stay). */
    fun newTracker(floorHeightM: Double, onFloor: (Int) -> Unit): BarometerFloorTracker? {
        val s = source?.takeIf { it.available } ?: return null
        return BarometerFloorTracker(s, floorHeightM, clock, onFloor) { _status.value = it }
    }

    internal fun clear() { _status.value = null }
}
