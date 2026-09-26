package com.campusmaps.guidance

import com.campusmaps.data.campus.DemoBuildings
import com.campusmaps.data.model.Point
import com.campusmaps.routing.FloorChange
import com.campusmaps.routing.LockedNotice
import com.campusmaps.routing.Route
import com.campusmaps.routing.RoutePlan
import com.campusmaps.routing.Router
import com.campusmaps.routing.StepKind
import com.campusmaps.shared.WatchStepType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceEngineTest {

    private val cs = DemoBuildings.classroomSouth
    private val satNine = LocalDateTime.of(2026, 9, 1, 21, 0).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    // Library South entrance, by elevator, to Room 608.
    private val route: Route = (Router().plan(cs, "cs_p1", "cs_r608", satNine, avoidStairs = false) as RoutePlan.Options)
        .options.first().route

    private fun pose(nodeId: String, confidence: Float = 0.92f) =
        cs.node(nodeId).let { Pose(it.position, it.floor, 0.0, confidence) }

    @Test
    fun startsOnWalkToEntrance() {
        val progress = GuidanceEngine.update(route, pose("cs_p1"), Progress())
        val step = route.steps[progress.stepIndex]
        assertEquals(StepKind.WALK_TO_ENTRANCE, step.kind)
        assertEquals(45.0, GuidanceEngine.distanceToStep(route, step, progress), 0.01)
        assertEquals("Walk to Library South entrance", GuidanceEngine.bannerText(step, 45.0))
        assertEquals("Go through Library South entrance", GuidanceEngine.bannerText(step, 5.0))
    }

    @Test
    fun elevatorStepWaitsForTheRightFloor() {
        var progress = Progress()
        // Walk to the elevator lobby on floor 1 in a few hops so the tracker can follow.
        for (id in listOf("cs_p1", "cs_lib", "cs_libhall", "cs_elev1")) progress = GuidanceEngine.update(route, pose(id), progress)
        assertEquals(StepKind.ELEVATOR, route.steps[progress.stepIndex].kind)
        // Riding up: floor 3 in the shaft is still the elevator step.
        for (f in 2..5) progress = GuidanceEngine.update(route, pose("cs_elev$f"), progress)
        assertEquals(StepKind.ELEVATOR, route.steps[progress.stepIndex].kind)
        // Doors open on floor 6: the elevator step is done.
        progress = GuidanceEngine.update(route, pose("cs_elev6"), progress)
        assertTrue(route.steps[progress.stepIndex].kind != StepKind.ELEVATOR)
        assertFalse(progress.arrived)
        progress = GuidanceEngine.update(route, pose("cs_r608"), progress)
        assertTrue(progress.arrived)
    }

    @Test
    fun farFromThePathCountsAsOffRoute() {
        val start = pose("cs_p1")
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

        val locked = GuidanceEngine.watchStep(route.steps.first(), 45.0, LockedNotice("Main entrance", "West entrance"), showLocked = true)
        assertEquals(WatchStepType.LOCKED, locked.type)
        assertEquals("West entrance", locked.bigText)
        assertEquals("Main locked", locked.label)
    }

    @Test
    fun stairsUpAndDownGetDifferentWatchIcons() {
        val tueTen = LocalDateTime.of(2026, 9, 1, 10, 0).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
        fun stairsStep(from: String, to: String) =
            (Router().plan(cs, from, to, tueTen, avoidStairs = false) as RoutePlan.Options)
                .options.first { it.method == FloorChange.STAIRS }
                .route.steps.first { it.kind == StepKind.STAIRS }

        val up = GuidanceEngine.watchStep(stairsStep("cs_atrium", "cs_r220"), 5.0, null, showLocked = false)
        assertEquals(WatchStepType.STAIRS, up.type)
        assertEquals("Stairs up", up.label)
        assertEquals("Floor 2", up.bigText)

        val down = GuidanceEngine.watchStep(stairsStep("cs_r220", "cs_atrium"), 5.0, null, showLocked = false)
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
        assertEquals((0..route.steps.lastIndex).toList(), visited.toList())
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
