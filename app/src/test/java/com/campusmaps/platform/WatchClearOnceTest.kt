package com.campusmaps.platform

import org.junit.Assert.assertEquals
import org.junit.Test

// Mirrors MainViewModel: startSession -> routeStarted, stopSession -> routeEnded.
class WatchClearOnceTest {
    private var clears = 0
    private val gate = WatchClearOnce { clears++ }

    @Test
    fun doneClearsOnceAlthoughStopSessionRunsTwice() {
        gate.routeStarted()
        gate.routeEnded() // done(): stopSession()
        gate.routeEnded() // done(): reset() -> stopSession()
        assertEquals(1, clears)
    }

    @Test
    fun everyEndingPathClearsOncePerRoute() {
        // S2 End route, S3 Stop, S3 Back to routes, S3 Done, Reset demo: one route each.
        repeat(5) {
            gate.routeStarted()
            gate.routeEnded()
        }
        assertEquals(5, clears)
    }

    @Test
    fun resetWithoutARouteDoesNotClear() {
        gate.routeEnded() // Reset demo from S1
        assertEquals(0, clears)
    }

    @Test
    fun switchingRoutesMidSessionClearsOnlyAtTheEnd() {
        gate.routeStarted()
        gate.routeStarted() // S1b card tapped again without ending
        gate.routeEnded()
        assertEquals(1, clears)
    }
}
