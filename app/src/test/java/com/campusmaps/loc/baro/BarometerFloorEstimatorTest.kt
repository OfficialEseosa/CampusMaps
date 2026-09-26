package com.campusmaps.loc.baro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BarometerFloorEstimatorTest {
    private val base = 1012.90
    private val perFloor = 0.12 * 3.9 // Classroom South, 0.468 hPa
    private val periodMs = 100L       // SENSOR_DELAY_UI is about 15 Hz on phones; 10 Hz is enough here

    private fun calibrated(rng: Random, floor: Int = 2): Pair<BarometerFloorEstimator, Long> {
        val e = BarometerFloorEstimator(floorHeightM = 3.9)
        e.calibrate(floor, 0)
        var t = 0L
        while (e.calibrating) {
            e.onPressure(base + noise(rng), t)
            t += periodMs
        }
        return e to t
    }

    private fun noise(rng: Random) = (rng.nextDouble() * 2 - 1) * 0.05

    @Test fun calibratesOnTheMedianOfTheFirstTwoSeconds() {
        val (e, t) = calibrated(Random(1))
        assertEquals(2, e.floor)
        assertEquals(base, e.refPressure!!, 0.03)
        assertTrue(t in 2_000..2_200)
    }

    @Test fun fourFloorElevatorRideEndsOnFloor6WithoutFlicker() {
        for (seed in 1..20) {
            val rng = Random(seed)
            var (e, t) = calibrated(rng)
            val floors = mutableListOf(e.floor)
            fun feed(p: Double) {
                e.onPressure(p + noise(rng), t)?.let { floors += it }
                t += periodMs
            }
            repeat(20) { feed(base) } // 2 s waiting for the car
            e.rideStarted(t)
            // 12 s ride: 4 floors at a steady rate, then 3 s standing still at the door.
            val rideMs = 12_000L
            for (i in 0..(rideMs / periodMs)) feed(base - 4 * perFloor * i * periodMs / rideMs)
            repeat(30) { feed(base - 4 * perFloor) }
            e.rideEnded(t)
            repeat(50) { feed(base - 4 * perFloor) }

            assertEquals("seed $seed: $floors", 6, e.floor)
            // Only ever counts up, one floor at a time: no flicker back.
            assertEquals("seed $seed: $floors", floors.sorted().distinct(), floors)
            assertTrue("seed $seed: $floors", floors.zipWithNext().all { (a, b) -> b - a == 1 })
        }
    }

    @Test fun rideDownCountsDown() {
        val rng = Random(7)
        val e = BarometerFloorEstimator(floorHeightM = 3.9)
        e.calibrate(6, 0)
        var t = 0L
        while (e.calibrating) { e.onPressure(base + noise(rng), t); t += periodMs }
        e.rideStarted(t)
        for (i in 0..100) { e.onPressure(base + 4 * perFloor * i / 100 + noise(rng), t); t += periodMs }
        repeat(40) { e.onPressure(base + 4 * perFloor + noise(rng), t); t += periodMs }
        assertEquals(2, e.floor)
    }

    @Test fun weatherDriftOnAHallwayStepChangesNothing() {
        val rng = Random(3)
        var (e, t) = calibrated(rng)
        // 0.3 hPa over 10 minutes (about 0.64 of a CS floor), no ride.
        val durMs = 600_000L
        while (t < durMs) {
            assertEquals(null, e.onPressure(base - 0.3 * t / durMs + noise(rng), t))
            t += periodMs
        }
        repeat(100) { e.onPressure(base - 0.3 + noise(rng), t); t += periodMs }
        assertEquals(2, e.floor)
    }

    @Test fun driftStaysIgnoredAfterTheRideWindowCloses() {
        val rng = Random(4)
        var (e, t) = calibrated(rng)
        e.rideStarted(t)
        e.rideEnded(t)
        t += 21_000
        repeat(100) { e.onPressure(base - 0.35 + noise(rng), t); t += periodMs }
        assertEquals(2, e.floor)
    }

    @Test fun signFixReZeroes() {
        val rng = Random(5)
        var (e, t) = calibrated(rng)
        // Weather drifts by one floor's worth while walking on floor 2...
        repeat(100) { e.onPressure(base - perFloor + noise(rng), t); t += periodMs }
        assertEquals(2, e.floor)
        // ...then a sign on floor 2 re-zeroes, so the next ride counts from the new pressure.
        e.rezero(2, "sign fix")
        assertEquals(base - perFloor, e.refPressure!!, 0.05)
        e.rideStarted(t)
        repeat(60) { e.onPressure(base - perFloor + noise(rng), t); t += periodMs }
        assertEquals("no phantom floor 3 after the re-zero", 2, e.floor)
        for (i in 0..50) { e.onPressure(base - perFloor - perFloor * i / 50 + noise(rng), t); t += periodMs }
        repeat(30) { e.onPressure(base - 2 * perFloor + noise(rng), t); t += periodMs }
        assertEquals(3, e.floor)
    }

    @Test fun signFixOnAnotherFloorMovesTheReference() {
        val rng = Random(6)
        var (e, t) = calibrated(rng)
        repeat(30) { e.onPressure(base + noise(rng), t); t += periodMs }
        e.rezero(5, "sign fix")
        assertEquals(5, e.floor)
        assertEquals(5, e.refFloor)
        e.rideStarted(t)
        repeat(40) { e.onPressure(base + noise(rng), t); t += periodMs }
        assertEquals(5, e.floor)
    }
}
