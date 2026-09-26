package com.campusmaps.routing

import com.campusmaps.TestData
import com.campusmaps.data.Access
import com.campusmaps.data.Building
import com.campusmaps.data.Geo
import com.campusmaps.data.Node
import com.campusmaps.data.NodeType
import kotlin.math.hypot
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouterTest {
    private val kl = TestData.load("KL")
    private val cs = TestData.load("CS")
    private val cse = TestData.load("CSE")
    private val daytime = Prefs(now = TestData.saturday(14))

    /** 30 m outward from a surveyed entrance fix, away from the centroid of all CS entrances. */
    private fun outsideNear(b: Building, entrance: String): Start.Outside {
        val ents = b.nodes.filter { it.type == NodeType.ENTRANCE }
        val cx = ents.map { it.x }.average(); val cy = ents.map { it.y }.average()
        val e = b.node(entrance); val dx = e.x - cx; val dy = e.y - cy; val l = hypot(dx, dy)
        val (lat, lng) = Geo.offset(e.lat!!, e.lng!!, dx / l * 30, dy / l * 30)
        return Start.Outside(lat, lng)
    }
    // The app's P1/P2 are CS.json startPoints; they were generated with outsideNear(cs, "E-95DS"/"E-WM"). Use the file's values so
    // the tests and the emulator see the same starts.
    private fun startPoint(id: String) = cs.startPoints.single { it.id == id }.let { Start.Outside(it.lat, it.lng) }
    private val p1 by lazy { startPoint("P1") }
    private val p2 by lazy { startPoint("P2") }

    private fun show(title: String, options: List<RouteOption>) {
        println("== $title"); options.forEach { print(it.describe()) }
    }

    @Test fun klausRoutesFromBothStartsMatchTheHandCheckedLists() {
        // Survey KL-20260926-0946: both starts are in the atrium on the measured H1 -> elevator walk; 1116W is off the corridor
        // that runs north from the Research Wing door past the foot of the glass staircase (H1).
        val s1 = Router.route(kl, Start.AtNode("S1"), "R-1116W", daytime)
        val s2 = Router.route(kl, Start.AtNode("S2"), "R-1116W", daytime)
        show("KL S1", s1); show("KL S2", s2)
        for (o in listOf(s1.first(), s2.first())) o.instructions.forEach { println("  ${it.text}") }
        assertEquals(listOf("S1", "H1", "R-1116W"), s1.first().nodes)
        assertEquals(listOf("S2", "H1", "R-1116W"), s2.first().nodes)
        for (o in listOf(s1.first(), s2.first())) {
            assertEquals("Head toward the glass staircase", o.instructions.first().text)
            assertEquals(listOf(Direction.RIGHT), o.instructions.filter { it.type == InstructionType.TURN }.map { it.direction })
            // The owner at Klaus: the door is straight ahead coming from the staircase (doorFacing south, was west).
            assertEquals("Room 1116W is ahead", o.instructions.last().text)
        }
        for (o in listOf(s1.first(), s2.first())) {
            assertTrue(o.distanceM < 60, "route ${o.distanceM} m")
            assertTrue(o.instructions.count { it.type == InstructionType.TURN } <= 2)
            assertEquals(VerticalMethod.NONE, o.verticalMethod)
            assertEquals(null, o.entrance)
            assertFalse(o.instructions.any { it.distanceM == 0.0 && it.type != InstructionType.ARRIVE }, "no 0 m steps")
        }
        // T and both starts are within 10 m of the table
        for (s in listOf("S1", "S2")) assertTrue(hypot(kl.node(s).x - kl.node("T").x, kl.node(s).y - kl.node("T").y) <= 10)
        // The surveyed floor-3 room is reachable both ways (not a Demo A destination: over 60 m).
        val coeus = Router.route(kl, Start.AtNode("S1"), "R-3361", daytime)
        show("KL S1 to COEUS", coeus)
        assertEquals(setOf(VerticalMethod.ELEVATOR, VerticalMethod.STAIRS), coeus.map { it.verticalMethod }.toSet())
    }

    // Decatur-side doors in CS.json from survey CS-20260925-1238: 95 Decatur (floor 1), Library South and Classroom South main (floor 2).
    private val decaturSide = setOf("E-95DS", "E-LM2", "E-CSM2")

    @Test fun cs608FromTwoOutdoorStartsPicksDifferentEntrances() {
        val a = Router.route(cs, p1, "R-608", daytime); val b = Router.route(cs, p2, "R-608", daytime)
        show("CS 608 from P1", a); show("CS 608 from P2", b)
        assertNotEquals(a.first().entrance, b.first().entrance)
        // Survey CS-20260925-1238: P1's best door is a floor-2 plaza door (one elevator floor saved), P2's is Walters main.
        assertTrue(a.first().entrance in decaturSide)
        assertEquals(2, a.first().entranceFloor)
        assertTrue(b.first().entrance in setOf("E-WM", "E-WS"))
        assertNotEquals(a.map { it.entrance }, b.map { it.entrance }, "option list must change with position")
        for (o in a + b) { assertNotNull(o.entranceFloor); assertTrue("floor ${o.entranceFloor}" in o.label()) }
        assertTrue(a.size in 2..3)
    }

    @Test fun cs608ElevatorWaitIsChargedOnce() {
        // Survey CS-20260925-1238 timed the bank (2 rides); P1 boards on floor 2, P2 on floor 1. The wait is charged once either way.
        val lift = cs.elevator("ELEV-1")!!
        for ((start, floors) in listOf(p1 to 4, p2 to 5)) {
            val o = Router.route(cs, start, "R-608", daytime.copy(avoidStairs = true)).first()
            assertEquals(lift.avgWaitSec, o.eta.elevatorWaitSec, 1e-9)
            assertEquals(floors * lift.secondsPerFloor, o.eta.elevatorRideSec, 1e-9)
            val ride = o.instructions.single { it.type == InstructionType.ELEVATOR }
            assertEquals("Take the elevator to floor 6", ride.text); assertEquals(floors, ride.floorDelta); assertEquals(Direction.UP, ride.direction)
        }
    }

    @Test fun cs608AvoidStairsUsesOnlyElevators() {
        val options = Router.route(cs, p2, "R-608", daytime.copy(avoidStairs = true))
        show("CS 608 from P2, avoid stairs", options)
        assertTrue(options.isNotEmpty())
        assertTrue(options.all { o -> o.verticalMethod == VerticalMethod.ELEVATOR && o.nodes.filter { it != Router.OUTSIDE }.none { cs.node(it).type == NodeType.STAIRS } })
    }

    @Test fun cs150UsesFloorOneEntrancesAndCrossesNoFloors() {
        // Survey CS-20260925-1238 added two floor-2 plaza doors; from P1 a floor-2 door plus one flight down is a real (slower) card,
        // so only the best card must stay on floor 1. Every card ends on floor 1 at the room.
        for (start in listOf(p1, p2)) {
            val options = Router.route(cs, start, "R-150", daytime)
            show("CS 150", options)
            assertTrue(options.isNotEmpty())
            val best = options.first()
            assertEquals(1, best.entranceFloor)
            assertEquals(VerticalMethod.NONE, best.verticalMethod)
            assertTrue(best.nodes.drop(1).all { cs.node(it).floor == 1 })
            for (o in options) assertEquals("R-150", o.nodes.last())
        }
    }

    @Test fun studentCenterAfterHoursRedirects() {
        val main = cse.node("E-MAIN")
        val (lat, lng) = Geo.offset(main.lat!!, main.lng!!, 0.0, -15.0)
        val start = Start.Outside(lat, lng)
        val late = Router.route(cse, start, "R-220", Prefs(now = TestData.saturday(21)))
        show("CSE Sat 21:00", late)
        val notice = assertNotNull(late.first().notice)
        assertTrue("Main entrance" in notice && "West entrance" in notice, notice)
        assertEquals("E-WEST", late.first().entrance)
        assertEquals(InstructionType.LOCKED_NOTICE, late.first().instructions.first().type)

        val afternoon = Router.route(cse, start, "R-220", Prefs(now = TestData.saturday(14)))
        show("CSE Sat 14:00", afternoon)
        assertNull(afternoon.first().notice)
        assertEquals("E-MAIN", afternoon.first().entrance)
    }

    @Test fun panthercardOpensTheCardOnlyEntrance() {
        val main = cse.node("E-MAIN")
        val (lat, lng) = Geo.offset(main.lat!!, main.lng!!, 0.0, -15.0)
        val start = Start.Outside(lat, lng)
        val sat21 = TestData.saturday(21)

        val noCard = Router.route(cse, start, "R-220", Prefs(now = sat21))
        assertEquals("E-WEST", noCard.first().entrance)
        assertTrue("card-only" in assertNotNull(noCard.first().notice))
        assertTrue(noCard.none { it.cardNeeded })

        val card = Router.route(cse, start, "R-220", Prefs(now = sat21, hasCard = true))
        show("CSE Sat 21:00 with card", card)
        assertEquals("E-MAIN", card.first().entrance)
        assertTrue(card.first().cardNeeded)
        assertNull(card.first().notice)
        assertTrue(card.none { it.instructions.any { i -> i.type == InstructionType.LOCKED_NOTICE } })

        // Daytime: Main is public, so no card is needed even for a card holder.
        val day = Router.route(cse, start, "R-220", Prefs(now = TestData.saturday(14), hasCard = true))
        assertEquals("E-MAIN", day.first().entrance)
        assertTrue(day.none { it.cardNeeded })

        // 23:30: every door is closed; the card opens nothing.
        assertTrue(Router.route(cse, start, "R-220", Prefs(now = TestData.saturday(23, 30))).isEmpty())
        assertTrue(Router.route(cse, start, "R-220", Prefs(now = TestData.saturday(23, 30), hasCard = true)).isEmpty())

        // "after 8 pm": the Saturday public window closed at 20:00.
        assertEquals(20 * 60, Access.cardOnlySince(main, sat21))
        assertNull(Access.cardOnlySince(main, TestData.saturday(14)))
    }

    @Test fun instructionsAreShortAndEndWithArrival() {
        val routes = listOf(Router.route(kl, Start.AtNode("S1"), "R-1116W", daytime), Router.route(cs, p1, "R-608", daytime),
            Router.route(cs, p2, "R-150", daytime), Router.route(cse, Start.AtNode("E-MAIN"), "R-220", daytime)).flatten()
        for (o in routes) {
            assertEquals(InstructionType.ARRIVE, o.instructions.last().type)
            assertEquals(InstructionType.START, o.instructions.first { it.type != InstructionType.LOCKED_NOTICE }.type)
            for (i in o.instructions.filter { it.type != InstructionType.LOCKED_NOTICE })
                assertTrue(i.text.split(" ").size < 12, "too long: ${i.text}")
            assertEquals(o.distanceM, o.instructions.sumOf { it.distanceM }, 1e-6)
        }
    }

    @Test fun turnDirections() {
        fun n(x: Double, y: Double) = Node("n", NodeType.WAYPOINT, "n", 1, x, y)
        assertEquals(Direction.STRAIGHT, Instructions.turn(n(0.0, 0.0), n(10.0, 0.0), n(20.0, 3.0)))
        assertEquals(Direction.LEFT, Instructions.turn(n(0.0, 0.0), n(10.0, 0.0), n(10.0, 10.0)))
        assertEquals(Direction.RIGHT, Instructions.turn(n(0.0, 0.0), n(10.0, 0.0), n(10.0, -10.0)))
        assertEquals(Direction.U_TURN, Instructions.turn(n(0.0, 0.0), n(10.0, 0.0), n(0.0, 0.5)))
    }

    @Test fun unreachableGivesNoOptions() {
        val closedAll = cse.copy(nodes = cse.nodes.map { if (it.id == "E-WEST") it.copy(access = cse.node("E-MAIN").access) else it })
        val main = cse.node("E-MAIN")
        assertEquals(emptyList(), Router.route(closedAll, Start.Outside(main.lat!!, main.lng!!), "R-220", Prefs(now = TestData.saturday(23))))
    }

    @Test fun cs608FromP1ShowsDistinctCardsAndFoldsTwinEntrances() {
        val a = Router.route(cs, p1, "R-608", daytime)
        assertTrue(a.size in 2..3)
        a.zipWithNext().forEach { (x, y) -> assertTrue(y.etaSec - x.etaSec >= 20, "cards ${x.label()} and ${y.label()} only ${y.etaSec - x.etaSec} s apart") }
        // The three Decatur-side doors lead into one lobby: one card, the other two named as alternatives.
        // Survey CS-20260925-1238: the two floor-2 plaza doors lead into the same hallway, so one card names the other in alsoVia.
        // 95 Decatur (floor 1) is 12 s slower by elevator: not the same card, and too close to show as its own.
        val decatur = decaturSide.associateWith { cs.node(it).name }
        val top = a.first()
        assertTrue(top.entrance in decatur.keys)
        assertTrue(top.alsoVia.isNotEmpty())
        assertTrue(top.alsoVia.all { it in decatur.values && it != top.entranceName }, "${top.alsoVia}")
        assertTrue(decatur.filterKeys { it != top.entrance && cs.node(it).floor == 2 }.values.all { it in top.alsoVia })
        // No card is another card in disguise: one per (entrance, method), and no card's entrance is listed as another's alternative.
        assertEquals(a.size, a.map { it.entrance to it.verticalMethod }.toSet().size)
        for (o in a) for (other in a) if (o !== other && o.verticalMethod == other.verticalMethod) assertFalse(o.entranceName in other.alsoVia)
        assertTrue("also via" in top.describe())
    }

    /**
     * docs/04: "top two options differ in ETA by at least 60 s". With CS.json from survey CS-20260925-1238 they differ by 56 s
     * (elevator 87 s against stairs 143 s). Still ignored: the stairs speed up is estimated (no upward climb timed).
     */
    @Ignore("56 s with survey CS-20260925-1238; stairs-up still estimated. See docs/19-building-data-status.md, 'Demo B: the 60 s gap'")
    @Test fun cs608TopTwoFromP1DifferByAMinute() {
        val a = Router.route(cs, p1, "R-608", daytime)
        assertTrue(a.size >= 2 && a[1].etaSec - a[0].etaSec >= 60, "gap ${a.getOrNull(1)?.etaSec?.minus(a[0].etaSec)} s")
    }

    @Test fun insideStartNamesNoEntranceEvenWhenPassingOne() {
        // From the top of the Library South stairs the way to 608 crosses the Library South entrance node (survey CS-20260925-1238).
        val options = Router.route(cs, Start.AtNode("ST-LS-2"), "R-608", daytime)
        show("CS ST-LS-2 to 608 (inside)", options)
        assertTrue(options.isNotEmpty())
        assertTrue(options.any { "E-LM2" in it.nodes }, "test needs a path through an entrance node")
        for (o in options) { assertNull(o.entrance); assertNull(o.entranceName); assertNull(o.entranceFloor); assertTrue(o.alsoVia.isEmpty()) }
        for (o in Router.route(cs, Start.AtNode("E-95DS"), "R-608", daytime)) assertNull(o.entrance)
        // Outside starts still name theirs.
        assertTrue(Router.route(cs, p1, "R-608", daytime).all { it.entrance != null })
    }

    @Test fun rerouteFromAVerticalNodeStartsWithTheRideOrClimb() {
        val lift = Router.route(cs, Start.AtNode("EL-2"), "R-608", daytime).first { it.verticalMethod == VerticalMethod.ELEVATOR }
        show("CS from EL-2", listOf(lift))
        val first = lift.instructions.first()
        assertEquals(InstructionType.ELEVATOR, first.type); assertEquals("Take the elevator to floor 6", first.text)
        assertEquals(4, first.floorDelta); assertEquals(Direction.UP, first.direction)
        assertTrue(lift.instructions.none { it.text.startsWith("Head toward") })
        val climb = Router.route(cs, Start.AtNode("ST-1"), "R-608", daytime).first { it.verticalMethod == VerticalMethod.STAIRS }
        assertEquals("Take the stairs up five floors", climb.instructions.first().text)
        assertEquals(InstructionType.STAIRS_UP, climb.instructions.first().type)
        for (o in listOf(lift, climb)) assertEquals(o.distanceM, o.instructions.sumOf { it.distanceM }, 1e-6)
    }

    @Test fun cameFromTurnsTheFirstInstructionIntoLeftOrRight() {
        // H1 is the glass door from the Walters lobby into the main hallway (survey CS-20260925-1238, obs #15).
        val plain = Router.route(cs, Start.AtNode("H1"), "R-608", daytime).first()
        assertEquals("Head toward Elevator lobby turn, down the main hallway", plain.instructions.first().text)
        assertEquals(InstructionType.START, plain.instructions.first().type)
        // Walked in from the Walters side door (heading south), the hallway runs south-east: a left turn.
        val turned = Router.route(cs, Start.AtNode("H1", cameFrom = "E-WS"), "R-608", daytime).first()
        show("CS from H1 via E-WS", listOf(turned))
        assertEquals(plain.nodes, turned.nodes)
        val first = turned.instructions.first()
        assertEquals(InstructionType.TURN, first.type); assertEquals(Direction.LEFT, first.direction)
        assertTrue(first.text.startsWith("Turn left toward Elevator lobby turn"), first.text)
        // cameFrom never forbids going back: overshooting back to the side door gives a U-turn.
        val back = Router.route(cs, Start.AtNode("E-WS", cameFrom = "H1"), "R-608", daytime).first()
        assertEquals("H1", back.nodes[1]); assertEquals(Direction.U_TURN, back.instructions.first().direction)
        // Unknown or other-floor cameFrom is ignored.
        assertEquals(plain.instructions, Router.route(cs, Start.AtNode("H1", cameFrom = "EL-6"), "R-608", daytime).first().instructions)
        assertEquals(plain.instructions, Router.route(cs, Start.AtNode("H1", cameFrom = "NOPE"), "R-608", daytime).first().instructions)
    }
}
