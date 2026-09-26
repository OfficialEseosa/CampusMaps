package com.campusmaps.route

import com.campusmaps.data.campus.TestBuildings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RouteCardTextTest {
    private val friNoon = LocalDateTime.of(2026, 9, 25, 12, 0)

    // A real CS card to copy from (the Route inside is not used by the card text).
    private val base: RouteOption by lazy {
        (CoreRouter().plan(TestBuildings.cs, "P1", "R-608", friNoon, avoidStairs = true) as RoutePlan.Options).options.first()
    }

    private fun elevator(eta: Double, walk: Double, floors: Int, wait: Int = 2) =
        base.copy(method = FloorChange.ELEVATOR, etaSeconds = eta, walkM = walk, floorsChanged = floors, elevatorWaitS = wait, shortcutName = null)

    @Test
    fun twoElevatorCardsDoNotRepeatElevatorWait() {
        // docs/22 #12: was "+54 s: 64 m more walking, elevator, avg wait 2 s".
        val best = elevator(eta = 87.0, walk = 60.0, floors = 4)
        val other = elevator(eta = 141.0, walk = 124.0, floors = 5)
        val text = RouteCardText.difference(other, best)
        assertEquals("+54 s: 64 m more walking, rides 5 floors", text)
        assertFalse(text.contains("avg wait"))
    }

    @Test
    fun sameElevatorRideSaysOnlyTheWalk() {
        val best = elevator(eta = 87.0, walk = 60.0, floors = 4)
        val other = elevator(eta = 100.0, walk = 80.0, floors = 4)
        assertEquals("+13 s: 20 m more walking", RouteCardText.difference(other, best))
    }

    @Test
    fun differentMethodStillNamesTheMethod() {
        val best = elevator(eta = 87.0, walk = 60.0, floors = 4)
        val stairs = best.copy(method = FloorChange.STAIRS, etaSeconds = 134.0, elevatorWaitS = null)
        assertEquals("+47 s: same walk, 4 floors of stairs", RouteCardText.difference(stairs, best))
        val back = RouteCardText.difference(best.copy(etaSeconds = 140.0), stairs)
        assertTrue(back, back.endsWith("elevator, avg wait 2 s"))
    }
}
