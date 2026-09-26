package com.campusmaps.guidance

import com.campusmaps.data.campus.TestBuildings
import com.campusmaps.data.model.Point
import com.campusmaps.route.FloorChange
import com.campusmaps.route.LockedNotice
import com.campusmaps.route.Route
import com.campusmaps.route.RoutePlan
import com.campusmaps.route.CoreRouter
import com.campusmaps.route.StepKind
import com.campusmaps.shared.WatchStepType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceEngineTest {

    private val cs = TestBuildings.cs
    private val friNoon = LocalDateTime.of(2026, 9, 25, 12, 0)

    // Classroom South, P1 (Decatur St side) to Room 608 by elevator, from the real building file through core.
    private val route: Route = (CoreRouter().plan(cs, "P1", "R-608", friNoon, avoidStairs = false) as RoutePlan.Options)
        .options.first { it.method == FloorChange.ELEVATOR }.route

    private fun pose(i: Int, confidence: Float = 0.92f) =
        route.points[i].let { Pose(it.position, it.floor, 0.0, confidence) }

    @Test
    fun startsOnWalkToEntrance() {
        val progress = GuidanceEngine.update(route, pose(0), Progress())
        val step = route.steps[progress.stepIndex]
        val entrance = route.points[1].node.name
        assertEquals(StepKind.WALK_TO_ENTRANCE, step.kind)
        assertEquals(route.points[1].cumulativeM, GuidanceEngine.distanceToStep(route, step, progress), 0.01)
        assertEquals("Walk to $entrance", GuidanceEngine.bannerText(step, 45.0))
        assertTrue(GuidanceEngine.bannerText(step, 5.0).startsWith("Go through $entrance"))
    }

    @Test
    fun elevatorStepWaitsForTheRightFloor() {
        var progress = Progress()
        val rideStart = route.steps.first { it.kind == StepKind.ELEVATOR }.startIndex
        // Walk to the elevator on the entrance floor, one route point at a time so the tracker can follow.
        for (i in 0..rideStart) progress = GuidanceEngine.update(route, pose(i), progress)
        assertEquals(StepKind.ELEVATOR, route.steps[progress.stepIndex].kind)
        // Riding up: an in-between floor in the shaft is still the elevator step.
        val shaft = route.points[rideStart]
        progress = GuidanceEngine.update(route, Pose(shaft.position, shaft.floor + 1, 0.0, 0.92f), progress)
        assertEquals(StepKind.ELEVATOR, route.steps[progress.stepIndex].kind)
        // Doors open on floor 6: the elevator step is done.
        val top = (rideStart + 1..route.points.lastIndex).first { route.points[it].floor == 6 }
        progress = GuidanceEngine.update(route, pose(top), progress)
        assertTrue(route.steps[progress.stepIndex].kind != StepKind.ELEVATOR)
        assertFalse(progress.arrived)
        for (i in top + 1..route.points.lastIndex) progress = GuidanceEngine.update(route, pose(i), progress)
        assertTrue(progress.arrived)
    }

    @Test
    fun farFromThePathCountsAsOffRoute() {
        val start = pose(0)
        val progress = GuidanceEngine.update(route, start.copy(position = start.position + Point(0.0, 20.0)), Progress())
        assertTrue(progress.offRouteM > GuidanceEngine.OFF_ROUTE_M)
    }

    @Test
    fun watchStepsFollowTheHandoffTable() {
        val elevator = route.steps.first { it.kind == StepKind.ELEVATOR }
        val w = GuidanceEngine.watchStep(elevator, 10.0, null, showLocked = false)
        assertEquals(WatchStepType.ELEVATOR, w.type)
        assertEquals("Floor 6", w.bigText)
        assertEquals("Elevator", w.label)

        val arrive = route.steps.last()
        assertEquals(WatchStepType.ARRIVED, GuidanceEngine.watchStep(arrive, 0.0, null, false).type)

        val notice = LockedNotice.fromCore("Heads up: Main entrance is card-only now. Using West entrance instead.")
        val locked = GuidanceEngine.watchStep(route.steps.first(), 45.0, notice, showLocked = true)
        assertEquals(WatchStepType.LOCKED, locked.type)
        assertEquals("West entrance", locked.bigText)
        assertEquals("Main locked", locked.label)
    }

    @Test
    fun stairsUpAndDownGetDifferentWatchIcons() {
        fun stairsStep(from: String, to: String) =
            (CoreRouter().plan(cs, from, to, friNoon, avoidStairs = false) as RoutePlan.Options)
                .options.first { it.method == FloorChange.STAIRS }
                .route.steps.first { it.kind == StepKind.STAIRS }

        val up = GuidanceEngine.watchStep(stairsStep("H9", "R-608"), 5.0, null, showLocked = false)
        assertEquals(WatchStepType.STAIRS, up.type)
        assertEquals("Stairs up", up.label)
        assertEquals("Floor 6", up.bigText)

        val down = GuidanceEngine.watchStep(stairsStep("R-608", "H9"), 5.0, null, showLocked = false)
        assertEquals(WatchStepType.STAIRS_DOWN, down.type)
        assertEquals("Stairs down", down.label)
        assertEquals("Floor 1", down.bigText)
    }

    // End to end: the simulated student walks the whole route and every step shows in order.
    @Test
    fun simulatedWalkVisitsEveryStepAndArrives() = runTest {
        val start = route.points.first()
        val sim = SimulatedPositionProvider(
            scope = backgroundScope,
            startPose = Pose(start.position, start.floor, 0.0, 0.92f),
            startLost = false,
            signText = { "TEST" },
        )
        sim.follow(route)
        var progress = Progress()
        val visited = linkedSetOf<Int>()
        var ticks = 0
        while (!progress.arrived && ticks < 5_000) {
            advanceTimeBy(100)
            progress = GuidanceEngine.update(route, sim.pose.value, progress)
            visited += progress.stepIndex
            ticks++
        }
        sim.stop()
        assertTrue("Should arrive (stopped at step ${progress.stepIndex})", progress.arrived)
        // Core can give two instructions a few metres apart ("Exit toward Elevator lobby, floor 6", then "Turn left at
        // Elevator lobby, floor 6" 3 m later). GuidanceEngine completes a step within 1.5 m of its end, so the first of such
        // a pair is passed in the same tick. Every other step must show, in order.
        val shadowed = route.steps.indices.filter { k ->
            val next = route.steps.getOrNull(k + 1)
            next != null && next.completeFloor == route.steps[k].completeFloor && next.completeAtM - route.steps[k].completeAtM < 3.0
        }
        assertEquals((0..route.steps.lastIndex).filter { it !in shadowed }, visited.filter { it !in shadowed })
    }

    // Starting outside, the simulator first "looks for a sign" (Locate me), then recovers.
    @Test
    fun simulatedStartOutsideShowsLocateMeFirst() = runTest {
        val start = route.points.first()
        val sim = SimulatedPositionProvider(backgroundScope, Pose(start.position, start.floor, 0.0, 0.3f), startLost = true, signText = { "ROOM 608" })
        sim.follow(route)
        advanceTimeBy(500)
        assertTrue(sim.pose.value.confidence < LOCATE_CONFIDENCE)
        advanceTimeBy(1_500)
        assertEquals("ROOM 608", sim.pose.value.sign?.text)
        advanceTimeBy(3_000)
        assertTrue(sim.pose.value.confidence >= LOCATE_CONFIDENCE)
        sim.stop()
    }
}
