package com.campusmaps.loc.baro

import org.junit.Assert.assertEquals
import org.junit.Test

/** A ride whose pressure change reads short (filter lag, a short elevator hop) still lands on the ride's target floor. */
class RideTargetSnapTest {
    private fun ride(target: Int?, dropHpa: Double): Int {
        val e = BarometerFloorEstimator(floorHeightM = 4.7) // Klaus: 0.564 hPa per floor
        var t = 0L
        e.calibrate(1, t)
        while (t <= 2_500) { e.onPressure(1000.0, t); t += 100 }
        e.rideStarted(t, target)
        // Pressure falls over 6 s, then holds; only dropHpa of change arrives (1.9 floors would be 1.06 hPa).
        val end = t + 6_000
        while (t <= end + 6_000) {
            val p = 1000.0 - dropHpa * ((t - (end - 6_000)).coerceIn(0, 6_000) / 6_000.0)
            e.onPressure(p, t); t += 100
        }
        return e.floor
    }

    @Test fun shortReadingWithoutTargetParksOneFloorShort() = assertEquals(2, ride(target = null, dropHpa = 0.75))

    @Test fun shortReadingWithTargetSnapsToTheTargetFloor() = assertEquals(3, ride(target = 3, dropHpa = 0.75))

    @Test fun fullReadingWithTargetAlsoLandsOnTarget() = assertEquals(3, ride(target = 3, dropHpa = 1.06))

    @Test fun farFromTargetDoesNotSnap() = assertEquals(1, ride(target = 3, dropHpa = 0.2))
}
