package com.campusmaps.loc.baro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A barometer the test drives by hand. */
class FakePressureSource(override val available: Boolean = true) : PressureSource {
    private var sink: ((Double, Long) -> Unit)? = null
    var started = false
        private set

    override fun start(onReading: (hPa: Double, timeMs: Long) -> Unit): Boolean {
        if (!available) return false
        sink = onReading
        started = true
        return true
    }

    override fun stop() { sink = null; started = false }

    fun emit(hPa: Double, timeMs: Long) { sink?.invoke(hPa, timeMs) }
}

class BarometerFloorTrackerTest {
    @Test fun elevatorRideReportsEachFloorOnce() {
        val src = FakePressureSource()
        var t = 0L
        val floors = mutableListOf<Int>()
        val statuses = mutableListOf<BaroStatus>()
        val tr = BarometerFloorTracker(src, 3.9, { t }, { floors += it }, { statuses += it })
        assertTrue(tr.start(2))
        while (t <= 2_000) { src.emit(1012.90, t); t += 100 }
        assertTrue(tr.ready)
        tr.rideStarted()
        for (i in 0..100) { src.emit(1012.90 - 4 * 0.468 * i / 100, t); t += 100 }
        repeat(30) { src.emit(1012.90 - 4 * 0.468, t); t += 100 }
        tr.rideEnded()
        assertEquals(listOf(3, 4, 5, 6), floors)
        assertTrue(tr.movedSinceRideStart() == 0.0) // reset after the ride
        assertTrue(statuses.last().debugLine().contains("est floor 6"))
        tr.stop()
        assertFalse(src.started)
    }

    @Test fun noBarometerNoTracker() {
        BaroFeed.install(FakePressureSource(available = false)) { 0L }
        assertNull(BaroFeed.newTracker(3.9) {})
    }
}
