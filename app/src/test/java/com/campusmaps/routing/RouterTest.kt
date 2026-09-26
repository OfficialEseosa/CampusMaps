package com.campusmaps.routing

import com.campusmaps.data.campus.DemoBuildings
import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.EdgeSource
import com.campusmaps.data.model.GraphEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

class RouterTest {

    private val router = Router()
    private val cs = DemoBuildings.classroomSouth

    // A fixed date on the given weekday, so tests never depend on today.
    private fun at(day: DayOfWeek, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 9, 1, hour, minute).with(TemporalAdjusters.nextOrSame(day))

    private val satNine = at(DayOfWeek.SATURDAY, 21)
    private val satEleven = at(DayOfWeek.SATURDAY, 23)
    private val tueTen = at(DayOfWeek.TUESDAY, 10)

    private fun options(plan: RoutePlan): RoutePlan.Options {
        assertTrue("Expected route options but got $plan", plan is RoutePlan.Options)
        return plan as RoutePlan.Options
    }

    @Test
    fun weekdayMorningUsesTheMainEntrance() {
        val plan = options(router.plan(cs, "cs_p1", "cs_r608", tueTen, avoidStairs = false))
        val best = plan.options.first()
        assertEquals("Main entrance", best.entrance?.name)
        assertEquals(FloorChange.ELEVATOR, best.method)
        assertNull("Nothing is locked on a weekday morning", plan.lockedNotice)
    }

    @Test
    fun saturdayEveningSkipsTheLockedMainEntranceAndSaysSo() {
        val plan = options(router.plan(cs, "cs_p1", "cs_r608", satNine, avoidStairs = false))
        val best = plan.options.first()
        assertEquals("Library South entrance", best.entrance?.name)
        assertEquals(FloorChange.ELEVATOR, best.method)
        assertNotNull(plan.lockedNotice)
        assertEquals(
            "Main entrance is card-only now. Using Library South entrance instead.",
            plan.lockedNotice!!.text,
        )
        // No option may go through the locked door.
        assertTrue(plan.options.none { it.entrance?.id == "cs_main" })
    }

    @Test
    fun nearlyEqualEntrancesAreFoldedIntoAlsoVia() {
        val plan = options(router.plan(cs, "cs_p1", "cs_r608", satNine, avoidStairs = false))
        assertEquals(listOf("95 Decatur Street entrance"), plan.options.first().alsoVia)
        assertEquals("also via: 95 Decatur Street entrance", RouteCardText.alsoVia(plan.options.first()))
    }

    @Test
    fun oneToThreeCardsBestFirst() {
        val plan = options(router.plan(cs, "cs_p1", "cs_r608", satNine, avoidStairs = false))
        assertTrue(plan.options.size in 1..3)
        val etas = plan.options.map { it.etaSeconds }
        assertEquals(etas.sorted(), etas)
        assertTrue("Stairs should be offered when stairs are allowed", plan.options.any { it.method == FloorChange.STAIRS })
    }

    @Test
    fun avoidStairsRemovesEveryStairsRoute() {
        val plan = options(router.plan(cs, "cs_p1", "cs_r608", satNine, avoidStairs = true))
        assertTrue(plan.options.isNotEmpty())
        assertTrue(plan.options.all { it.method != FloorChange.STAIRS })
        for (option in plan.options) {
            assertTrue(option.route.points.none { it.arrivedBy == EdgeKind.STAIRS })
        }
    }

    @Test
    fun everyEntranceLockedGivesTheNoRouteMessage() {
        val plan = router.plan(cs, "cs_p1", "cs_r220", satEleven, avoidStairs = false)
        assertTrue(plan is RoutePlan.NoRoute)
        assertEquals(
            "No route to Room 220: every entrance is card-only at Sat 23:00.",
            (plan as RoutePlan.NoRoute).message,
        )
    }

    @Test
    fun insideStartIgnoresLocksAndSaysFromHere() {
        val plan = options(router.plan(cs, "cs_atrium", "cs_r608", satEleven, avoidStairs = false))
        val best = plan.options.first()
        assertNull(best.entrance)
        assertEquals("From here, by elevator", RouteCardText.title(best, startsInside = true))
        assertNull(plan.lockedNotice)
    }

    @Test
    fun startEqualsDestinationIsAlreadyHere() {
        val plan = router.plan(cs, "cs_r608", "cs_r608", satNine, avoidStairs = false)
        assertTrue(plan is RoutePlan.AlreadyHere)
        val step = (plan as RoutePlan.AlreadyHere).route.steps.single()
        assertEquals(StepKind.ALREADY_THERE, step.kind)
        assertEquals("You are at Room 608", step.text)
    }

    @Test
    fun libraryRouteHasTheExpectedInstructions() {
        val plan = options(router.plan(cs, "cs_p1", "cs_r608", satNine, avoidStairs = false))
        val steps = plan.options.first().route.steps
        assertEquals(StepKind.WALK_TO_ENTRANCE, steps.first().kind)
        assertEquals("Walk to Library South entrance", steps.first().text)
        assertEquals("Go through Library South entrance", steps.first().approachText)
        assertTrue(steps.any { it.kind == StepKind.ELEVATOR && it.text == "Take the elevator to floor 6" })
        assertEquals(StepKind.ARRIVE, steps.last().kind)
        // Coming from the elevator, Room 608 is on the right (as in the design).
        assertEquals("Room 608 is on your right", steps.last().text)
        assertEquals(Side.RIGHT, steps.last().side)
    }

    @Test
    fun elevatorWaitIsPaidOncePerRide() {
        val config = RoutingConfig()
        val plan = options(router.plan(cs, "cs_atrium", "cs_r608", tueTen, avoidStairs = true))
        val best = plan.options.first()
        // Atrium -> elevator is 30 m, elevator 6 -> Room 608 is 10 m.
        val expected = 40.0 / config.walkSpeedMps + config.elevatorWaitS + 5 * config.elevatorPerFloorS
        assertEquals(expected, best.etaSeconds, 0.01)
    }

    @Test
    fun stairsInstructionsUseWords() {
        val plan = options(router.plan(cs, "cs_atrium", "cs_r220", tueTen, avoidStairs = false))
        val stairs = plan.options.first { it.method == FloorChange.STAIRS }
        assertTrue(stairs.route.steps.any { it.text == "Take the stairs up one floor" })
    }

    @Test
    fun approvedStudentShortcutShowsAsItsOwnTaggedCard() {
        val shortcut = GraphEdge("cs_p1", "cs_walters", EdgeKind.WALK, 40.0, EdgeSource.STUDENT, "Sparks side cut through")
        val plan = options(router.plan(cs, "cs_p1", "cs_r608", satNine, avoidStairs = false, extraEdges = listOf(shortcut)))
        val tagged = plan.options.filter { it.usesStudentShortcut }
        assertTrue("A student shortcut card should be offered", tagged.isNotEmpty())
        assertEquals("Sparks side cut through", tagged.first().shortcutName)
        assertFalse(plan.options.first().usesStudentShortcut)
    }

    @Test
    fun crossFloorStudentWalkEdgeIsIgnored() {
        val bogus = GraphEdge("cs_r150", "cs_r608", EdgeKind.WALK, 24.0, EdgeSource.STUDENT, "Room 150 cut through")
        val plan = options(router.plan(cs, "cs_r150", "cs_r608", tueTen, avoidStairs = false, extraEdges = listOf(bogus)))
        assertTrue(plan.options.none { it.usesStudentShortcut })
    }

    @Test
    fun rerouteHeadingChangesTheFirstInstruction() {
        // Standing in the atrium facing east (towards the elevator) -> "Continue toward".
        val east = 0.0
        val facingEast = router.bestRoute(cs, "cs_atrium", "cs_r608", tueTen, avoidStairs = true, startHeadingRad = east)!!
        assertEquals("Continue toward Elevator lobby", facingEast.steps.first().text)
        // Facing west -> "Turn around toward".
        val facingWest = router.bestRoute(cs, "cs_atrium", "cs_r608", tueTen, avoidStairs = true, startHeadingRad = Math.PI)!!
        assertEquals("Turn around toward Elevator lobby", facingWest.steps.first().text)
    }

    @Test
    fun everyDemoDestinationIsReachableInEveryBuilding() {
        for (building in DemoBuildings.all) {
            for (destination in building.demoDestinationIds) {
                val plan = router.plan(building, building.defaultStartId, destination, tueTen, avoidStairs = false)
                assertTrue("${building.name} -> $destination: $plan", plan is RoutePlan.Options)
                val stepFree = router.plan(building, building.defaultStartId, destination, tueTen, avoidStairs = true)
                assertTrue("${building.name} -> $destination step free: $stepFree", stepFree is RoutePlan.Options)
            }
        }
    }
}
