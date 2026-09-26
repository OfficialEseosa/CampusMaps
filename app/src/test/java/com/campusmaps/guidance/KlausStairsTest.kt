package com.campusmaps.guidance

import com.campusmaps.data.campus.TestBuildings
import com.campusmaps.data.model.Point
import com.campusmaps.route.CoreRouter
import com.campusmaps.route.FloorChange
import com.campusmaps.route.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

// The real KL.json: the glass staircase is one flight edge ST-1 (floor 1) to ST-3 (floor 3), and guidance follows a
// climber anywhere along it, and the reroute tolerance is wide until a compass placement is refined.
class KlausStairsTest {
    private val kl = TestBuildings.kl
    private val friNoon = LocalDateTime.of(2026, 9, 25, 12, 0)
    private val route = CoreRouter().bestRoute(kl, "S1", "R-3361", friNoon, avoidStairs = false, preferMethod = FloorChange.STAIRS)!!

    @Test fun oneStairsStepUpTwoFloors() {
        val stairs = route.steps.filter { it.kind == StepKind.STAIRS }
        assertEquals(1, stairs.size)
        val s = stairs.single()
        assertEquals("Take the stairs up two floors", s.text)
        assertEquals(1, s.fromFloor)
        assertEquals(3, s.targetFloor)
        assertEquals(3, s.completeFloor)
        assertTrue(route.points.none { it.floor == 2 })
        assertTrue(kl.nodes.values.none { it.floor == 2 && it.id.startsWith("ST") })
        val ids = route.points.map { it.node.id }
        assertEquals(ids.indexOf("ST-1") + 1, ids.indexOf("ST-3"))
    }

    @Test fun climberHalfWayUpTheFlightIsOnTheRoute() {
        val ids = route.points.map { it.node.id }
        val i = ids.indexOf("ST-1")
        val a = route.points[i].position; val b = route.points[i + 1].position
        val mid = Point((a.x + b.x) / 2 + 1.0, (a.y + b.y) / 2)
        // Standing at the stair foot on the stairs step, then half way up (the barometer says floor 2, one metre off the line).
        val onStairs = Progress(stepIndex = route.steps.indexOfFirst { it.kind == StepKind.STAIRS }, segmentIndex = i, alongM = route.points[i].cumulativeM)
        val p = GuidanceEngine.update(route, Pose(mid, 2, 0.0, 1f), onStairs)
        assertTrue("off ${p.offRouteM}", p.offRouteM < 1.5)
        assertEquals(i, p.segmentIndex)
        assertEquals(StepKind.STAIRS, route.steps[p.stepIndex].kind)
    }

    @Test fun rerouteToleranceIsWideUntilRefined() {
        assertEquals(12.0, GuidanceEngine.offRouteLimitM(compassPlaced = true, refined = false), 0.0)
        assertEquals(6.0, GuidanceEngine.offRouteLimitM(compassPlaced = true, refined = true), 0.0)
        assertEquals(6.0, GuidanceEngine.offRouteLimitM(compassPlaced = false, refined = false), 0.0)
        // 9 m beside the first hallway (a 25 degree compass error after 20 m): no reroute before refinement, one after.
        val a = route.points[0].position; val b = route.points[1].position
        val dx = b.x - a.x; val dy = b.y - a.y; val l = kotlin.math.hypot(dx, dy)
        val side = Point((a.x + b.x) / 2 - dy / l * 9.0, (a.y + b.y) / 2 + dx / l * 9.0)
        val p = GuidanceEngine.update(route, Pose(side, route.points[0].floor, 0.0, 1f), Progress())
        assertTrue(p.offRouteM > GuidanceEngine.offRouteLimitM(true, true))
        assertTrue(p.offRouteM < GuidanceEngine.offRouteLimitM(true, false))
    }
}
