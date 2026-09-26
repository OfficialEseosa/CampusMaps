package com.campusmaps.data

import com.campusmaps.TestData
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildingDataTest {
    private val codes = listOf("KL", "CS", "CSE")

    @Test fun allThreeFilesLoadAndValidate() {
        for (code in codes) {
            val b = TestData.load(code)
            assertEquals(code, b.code)
            // Rule 10 (INFO) fires for every entrance without posted hours; only ERROR and WARN must be clean.
            assertEquals(emptyList(), BuildingValidator.validate(b).filter { it.severity != Severity.INFO }, "validator problems in $code")
        }
    }

    @Test fun everyGuessIsFlagged() {
        // Survey CSE-20260926-1530: all three nodes and both edges are walked; only the anchors (widths not taped) are estimated.
        val cse = TestData.load("CSE")
        assertTrue(cse.nodes.none { it.estimated } && cse.edges.none { it.estimated })
        assertTrue(cse.anchors.all { it.estimated })
        // Survey KL-20260926-0946: the door fix and the walked nodes are unestimated; S1, S2, T, the stair nodes, floor 3 are placed.
        val kl = TestData.load("KL")
        assertEquals(setOf("E-RWD", "H1", "H2", "EL-1", "R-1116W"), kl.nodes.filter { !it.estimated }.map { it.id }.toSet())
        assertTrue(kl.edges.filter { !it.estimated }.all { it.notes!!.startsWith("KL-20260926-0946") })
        assertTrue(kl.nodes.filter { it.estimated }.all { !it.notes.isNullOrBlank() })
        val cs = TestData.load("CS")
        // Survey CS-20260925-1238: only the four outdoor GPS fixes are unestimated positions (E-WS's fix was rejected); measured edges
        // are walk #16 (H1-H2), walk #37 (E-LM2-E-CSM2) and the five vertical edges timed by rides and descents.
        val surveyed = setOf("E-WM", "E-95DS", "E-LM2", "E-CSM2")
        assertEquals(surveyed, cs.nodes.filter { !it.estimated }.map { it.id }.toSet())
        assertEquals(7, cs.edges.count { !it.estimated })
        assertEquals(5, cs.edges.count { !it.estimated && it.kind.vertical })
        assertTrue(cs.edges.filter { !it.estimated && !it.kind.vertical }.all { it.notes!!.startsWith("CS-20260925-1238 walk") })
    }

    @Test fun roundTripKeepsTheBuilding() {
        val b = TestData.load("CS")
        assertEquals(b, BuildingLoader.fromJson(BuildingLoader.toJson(b)))
    }

    @Test fun unknownKeysAreIgnored() {
        val b = BuildingLoader.fromJson(tiny.replace("\"code\"", "\"futureField\": 1, \"code\""))
        assertEquals("T", b.code)
    }

    @Test fun badEdgeIdFails() {
        val b = BuildingLoader.fromJson(tiny).let { it.copy(edges = it.edges + Edge("A", "NOPE", 3.0)) }
        assertTrue(BuildingValidator.validate(b).any { it.rule == 1 && "NOPE" in it.message })
    }

    @Test fun severitiesAndRules9And10() {
        val t = BuildingLoader.fromJson(tiny)
        assertEquals(listOf(Problem(10, "entrance E has no access windows (treated as always public)", Severity.INFO)), BuildingValidator.validate(t))
        val close = t.copy(nodes = t.nodes + Node("A2", NodeType.WAYPOINT, "near A", 1, 5.3, 0.0) + Node("A3", NodeType.WAYPOINT, "above A", 2, 5.0, 0.0),
            edges = t.edges + Edge("A", "A2", 0.3) + Edge("A2", "A3", 0.0))
        val warn = BuildingValidator.validate(close).filter { it.rule == 9 }
        assertEquals(1, warn.size, "$warn"); assertEquals(Severity.WARN, warn.single().severity)
        assertTrue(warn.single().toString().startsWith("rule 9 (warn): nodes A and A2"), warn.single().toString())
        val open = t.copy(nodes = t.nodes.map { if (it.id == "E") it.copy(access = listOf(AccessWindow("Daily", "00:00", "24:00", AccessRule.PUBLIC))) else it })
        assertEquals(emptyList(), BuildingValidator.validate(open))
        val bad = BuildingValidator.validate(t.copy(edges = t.edges + Edge("A", "NOPE", 3.0))).single { it.rule == 1 }
        assertEquals(Severity.ERROR, bad.severity); assertEquals("rule 1: edge A->NOPE references missing node NOPE", bad.toString())
        assertEquals(5, BuildingValidator.validate(TestData.load("CS")).count { it.rule == 10 && it.severity == Severity.INFO })
    }

    @Test fun disconnectedGraphFails() {
        val b = BuildingLoader.fromJson(tiny).let { it.copy(nodes = it.nodes + Node("Z", NodeType.WAYPOINT, "island", 1, 50.0, 50.0)) }
        assertTrue(BuildingValidator.validate(b).any { it.rule == 5 && "Z" in it.message })
    }

    @Test fun elevatorAndVerticalRulesFail() {
        val b = BuildingLoader.fromJson(tiny).let {
            it.copy(nodes = it.nodes + Node("EL-1", NodeType.ELEVATOR, "lift", 1, 0.0, 0.0, elevatorId = "NOPE") + Node("ST-2", NodeType.STAIRS, "st", 2, 0.0, 0.0),
                edges = it.edges + Edge("A", "EL-1", 1.0) + Edge("EL-1", "ST-2", 0.0, EdgeKind.ELEVATOR, 1))
        }
        val rules = BuildingValidator.validate(b).map { it.rule }.toSet()
        assertTrue(3 in rules && 4 in rules, "got $rules")
    }

    @Test fun demoDestinationNeedsAnAnchorAndImagesAreChecked() {
        val b = TestData.load("CS")
        assertTrue(BuildingValidator.validate(b.copy(anchors = b.anchors.filter { it.floor != 6 })).any { it.rule == 6 })
        assertTrue(BuildingValidator.validate(b, imageExists = { false }).any { it.rule == 7 && "CS-A01" in it.message })
    }

    @Test fun csImageAnchorFilesExistAndAreSmall() {
        val cs = TestData.load("CS")
        val assets = TestData.buildingFile("CS").parentFile.parentFile
        val images = cs.anchors.filter { it.kind == AnchorKind.IMAGE }
        assertTrue(images.isNotEmpty())
        for (a in images) {
            val f = java.io.File(assets, a.image!!)
            assertTrue(f.isFile, "missing ${f.path}"); assertTrue(f.length() < 400_000, "${f.name} is ${f.length()} bytes")
            assertEquals("anchors/CS/${a.id}.jpg", a.image)
        }
        assertEquals(emptyList(), BuildingValidator.validate(cs, imageExists = { java.io.File(assets, it).isFile }).filter { it.rule == 7 })
    }

    @Test fun anchorSpacingRule8() {
        val kl = TestData.load("KL")
        val route = listOf("S2", "H1", "R-1116W")
        assertEquals(emptyList(), BuildingValidator.checkAnchorSpacing(kl, route))
        val sparse = kl.copy(anchors = kl.anchors.filter { it.node != "H1" })
        assertTrue(BuildingValidator.checkAnchorSpacing(sparse, route).any { it.rule == 8 })
    }

    @Test fun dayGroups() {
        assertEquals(DayOfWeek.entries.take(5).toSet(), Access.parseDays("Mon-Fri"))
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), Access.parseDays("Sat-Sun"))
        assertEquals(setOf(DayOfWeek.SATURDAY), Access.parseDays("Sat"))
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY), Access.parseDays("Fri-Mon"))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), Access.parseDays("Mon,Wed"))
    }

    @Test fun accessWindowsWrapMidnight() {
        val night = AccessWindow("Fri", "22:00", "02:00", AccessRule.PUBLIC)
        val fri = LocalDateTime.of(2026, 9, 25, 23, 30)
        assertTrue(Access.covers(night, fri))
        assertTrue(Access.covers(night, fri.plusHours(2)))          // Sat 01:30, tail of Friday's window
        assertTrue(!Access.covers(night, fri.plusHours(3)))         // Sat 02:30
        assertTrue(!Access.covers(night, fri.minusDays(1).plusHours(2))) // Fri 01:30 belongs to Thursday, not listed
        assertTrue(Access.covers(AccessWindow("Sat-Sun", "00:00", "24:00", AccessRule.CARD), TestData.saturday(23, 59)))
        val door = Node("E", NodeType.ENTRANCE, "door", 1, 0.0, 0.0, access = listOf(night))
        assertEquals(AccessRule.PUBLIC, Access.ruleAt(door, fri))
        assertEquals(AccessRule.CLOSED, Access.ruleAt(door, fri.minusHours(5)))   // no window covers it: closed, not card
        val carded = door.copy(access = listOf(night, AccessWindow("Fri", "12:00", "22:00", AccessRule.CARD)))
        assertEquals(AccessRule.CARD, Access.ruleAt(carded, fri.minusHours(5)))
        assertEquals(AccessRule.PUBLIC, Access.ruleAt(carded, fri))
        assertEquals(AccessRule.CLOSED, Access.ruleAt(carded.copy(access = carded.access!! + AccessWindow("Fri", "18:00", "19:00", AccessRule.CLOSED)), fri.minusHours(5)))
        assertEquals(AccessRule.PUBLIC, Access.ruleAt(door.copy(access = null), fri.minusHours(5)))
    }

    @Test fun geoHelpers() {
        val (lat, lng) = Geo.offset(33.75, -84.38, 30.0, 40.0)
        assertEquals(50.0, Geo.haversineM(33.75, -84.38, lat, lng), 0.05)
        val (x, y) = Geo.toBuilding(Origin("o", 33.75, -84.38, 90.0), lat, lng) // +y points east
        assertEquals(-40.0, x, 0.05); assertEquals(30.0, y, 0.05)
        assertEquals("north", Geo.compassWord(9.9)); assertEquals("east", Geo.compassWord(81.4)); assertEquals("northwest", Geo.compassWord(-60.0))
    }

    private val tiny = """{"code":"T","name":"Tiny","origin":{"description":"o","lat":0,"lng":0},
        "nodes":[{"id":"E","type":"entrance","name":"Door","floor":1,"x":0,"y":0,"lat":33.75,"lng":-84.38,"headingDeg":90},{"id":"A","type":"room","name":"Room A","floor":1,"x":5,"y":0}],
        "edges":[{"from":"E","to":"A","lengthM":5,"kind":"hallway"}]}"""
}
