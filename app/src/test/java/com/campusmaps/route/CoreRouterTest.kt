package com.campusmaps.route

import com.campusmaps.data.campus.TestBuildings
import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.EdgeSource
import com.campusmaps.data.model.GraphEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

// The three demos (docs/01) through the adapter: core Router.route(...) in, teammate RoutePlan out.
class CoreRouterTest {
    private val router = CoreRouter()
    private val satNine = LocalDateTime.of(2026, 9, 26, 21, 0)
    private val satTwo = LocalDateTime.of(2026, 9, 26, 14, 0)
    private val friNoon = LocalDateTime.of(2026, 9, 25, 12, 0)

    private fun options(plan: RoutePlan) = (plan as RoutePlan.Options).options

    @Test
    fun demoA_klausFromBothExpoStartsArrives() {
        val kl = TestBuildings.kl
        for (start in listOf("S1", "S2")) {
            val best = options(router.plan(kl, start, "R-1116W", friNoon, avoidStairs = false)).first()
            assertNull(best.entrance) // inside start: "From here"
            assertEquals(StepKind.ARRIVE, best.route.steps.last().kind)
            assertEquals(start, best.route.points.first().node.id)
            assertEquals("R-1116W", best.route.points.last().node.id)
        }
    }

    @Test
    fun demoB_twoStartPointsPickDifferentEntrancesFor608() {
        val cs = TestBuildings.cs
        val p1 = options(router.plan(cs, "P1", "R-608", friNoon, avoidStairs = false)).first()
        val p2 = options(router.plan(cs, "P2", "R-608", friNoon, avoidStairs = false)).first()
        val e1 = p1.entrance!!
        val e2 = p2.entrance!!
        assertNotEquals(e1.id, e2.id)
        println("P1 -> 608: ${e1.name} ${p1.method} ${Formats.eta(p1.etaSeconds)}; P2 -> 608: ${e2.name} ${p2.method} ${Formats.eta(p2.etaSeconds)}")
        // Walk to the entrance first, then core's words at the door.
        val first = p1.route.steps.first()
        assertEquals(StepKind.WALK_TO_ENTRANCE, first.kind)
        assertEquals("Walk to ${e1.name}", first.text)
        assertTrue(first.approachText!!.startsWith("Go through ${e1.name}"))
        // The card shows the entrance floor and an m:ss ETA.
        assertEquals("Enter on floor ${e1.floor} · ${RouteCardText.methodWords(p1.method)}", RouteCardText.subtitle(p1, startsInside = false, startFloor = 1))
        assertTrue(Regex("\\d+:\\d\\d").matches(Formats.eta(p1.etaSeconds)))
        // The outdoor start is tracked on the entrance's floor (CS has floor-2 entrances).
        assertEquals(p1.route.points[1].floor, p1.route.points[0].floor)
    }

    @Test
    fun demoB_avoidStairsRemovesStairsCards() {
        val cs = TestBuildings.cs
        val all = options(router.plan(cs, "P1", "R-608", friNoon, avoidStairs = false))
        val stepFree = options(router.plan(cs, "P1", "R-608", friNoon, avoidStairs = true))
        assertTrue(all.any { it.method == FloorChange.STAIRS })
        assertTrue(stepFree.none { it.method == FloorChange.STAIRS })
        assertTrue(stepFree.all { o -> o.route.points.none { it.arrivedBy == EdgeKind.STAIRS } })
    }

    @Test
    fun demoC_saturdayNightNamesBothEntrances() {
        val cse = TestBuildings.cse
        val plan = router.plan(cse, cse.defaultStartId, "R-220", satNine, avoidStairs = false) as RoutePlan.Options
        val notice = plan.lockedNotice!!
        assertEquals("Main entrance", notice.lockedEntrance)
        assertEquals("West entrance", notice.usingEntrance)
        assertEquals("Main entrance is card-only now. Using West entrance instead.", notice.text)
        assertEquals("West entrance", plan.options.first().entrance!!.name)
        // Afternoon: no banner, Main entrance first.
        val day = router.plan(cse, cse.defaultStartId, "R-220", satTwo, avoidStairs = false) as RoutePlan.Options
        assertNull(day.lockedNotice)
        assertEquals("Main entrance", day.options.first().entrance!!.name)
    }

    @Test
    fun everyEntranceLockedSaysSo() {
        val cse = TestBuildings.cse
        val plan = router.plan(cse, cse.defaultStartId, "R-220", LocalDateTime.of(2026, 9, 26, 23, 30), avoidStairs = false)
        assertEquals("No route to Room 220: every entrance is closed at Sat 23:30.", (plan as RoutePlan.NoRoute).message)
    }

    @Test
    fun startAtTheDestinationIsAlreadyHere() {
        assertTrue(router.plan(TestBuildings.cs, "R-608", "R-608", friNoon, avoidStairs = false) is RoutePlan.AlreadyHere)
    }

    // WHATS-LEFT open issue 1: a cross-floor "walk" shortcut must never become the best route.
    @Test
    fun crossFloorShortcutIsIgnoredSameFloorShortcutIsUsed() {
        val cs = TestBuildings.cs
        val cross = GraphEdge("R-150", "R-608", EdgeKind.WALK, 24.0, EdgeSource.STUDENT, "Magic stairs")
        val withCross = options(router.plan(cs, "R-150", "R-608", friNoon, avoidStairs = false, extraEdges = listOf(cross)))
        assertTrue(withCross.none { it.usesStudentShortcut })
        assertTrue(withCross.none { it.method == FloorChange.LEVEL })

        val cut = GraphEdge("R-150", "H9", EdgeKind.WALK, 5.0, EdgeSource.STUDENT, "Room 150 cut through")
        val withCut = options(router.plan(cs, "R-150", "R-608", friNoon, avoidStairs = false, extraEdges = listOf(cut)))
        assertTrue(withCut.first().usesStudentShortcut)
        assertEquals("Room 150 cut through", withCut.first().shortcutName)
    }

    @Test
    fun rerouteFromAnElevatorStartsWithTheRide() {
        val kl = TestBuildings.kl
        val r = router.bestRoute(kl, "EL-3", "R-1116W", friNoon, avoidStairs = false, preferMethod = FloorChange.ELEVATOR)!!
        assertEquals(StepKind.ELEVATOR, r.steps.first().kind)
        assertEquals("Take the elevator to floor 1", r.steps.first().text)
    }

    // Sweep (Raphael's NavLogicTest idea): every building x demo destination x start x avoid stairs x clock time.
    @Test
    fun sweepNeverProducesBrokenRoutes() {
        val times = listOf(6, 9, 13, 19, 21, 23).map { LocalDateTime.of(2026, 9, 26, it, 5) } + friNoon
        for (b in TestBuildings.all) for (dest in b.demoDestinationIds) for (start in b.startIds) for (avoid in listOf(false, true)) for (t in times) {
            when (val plan = router.plan(b, start, dest, t, avoid)) {
                is RoutePlan.Options -> for (o in plan.options) {
                    val steps = o.route.steps
                    assertTrue(steps.isNotEmpty())
                    assertEquals("${b.code} $start->$dest", StepKind.ARRIVE, steps.last().kind)
                    assertEquals(dest, o.route.points.last().node.id)
                    assertFalse(o.etaSeconds.isNaN())
                    steps.forEach { assertTrue("${b.code} $start->$dest: '${it.text}'", it.text.isNotBlank() && !it.text.contains("null")) }
                    assertEquals("${b.code} $start->$dest step order", steps.map { it.startIndex }.sorted(), steps.map { it.startIndex })
                    if (avoid) assertTrue(o.route.points.none { it.arrivedBy == EdgeKind.STAIRS })
                }
                is RoutePlan.NoRoute -> assertTrue(plan.message.startsWith("No route"))
                is RoutePlan.AlreadyHere -> assertEquals(start, dest)
            }
        }
    }
}
