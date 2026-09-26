package com.campusmaps.survey

import com.campusmaps.TestData
import com.campusmaps.data.AnchorKind
import com.campusmaps.data.BuildingLoader
import com.campusmaps.data.EdgeKind
import com.campusmaps.data.Geo
import com.campusmaps.data.NodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurveyConverterTest {
    private val json = TestData.resource("CS-20260924-1614.survey.json")
    private val b = SurveyConverter.convert(json)

    @Test fun countsMatchTheRealSurvey() {
        assertEquals("CS", b.code)
        assertEquals(6, b.nodes.size); assertEquals(2, b.edges.size); assertEquals(2, b.anchors.size)
        assertEquals(listOf("E-95DS", "E-LS", "E-LM", "E-WM", "E-WS", "R-150"), b.nodes.map { it.id })
        assertEquals(5, b.nodes.count { it.type == NodeType.ENTRANCE && !it.indoor && it.floor == 1 })
    }

    @Test fun entranceGpsIsProjected() {
        val o = b.origin
        assertEquals(33.75257984376145, o.lat, 1e-12)
        val e = b.node("E-95DS"); assertEquals(0.0, e.x, 1e-9); assertEquals(0.0, e.y, 1e-9)
        val wm = b.node("E-WM")
        assertEquals(Geo.haversineM(o.lat, o.lng, wm.lat!!, wm.lng!!), kotlin.math.hypot(wm.x, wm.y), 0.1)
        assertTrue(wm.x < -55 && wm.y > 30, "Walters main is north-west: ${wm.x}, ${wm.y}")
        assertEquals(35.8, wm.headingDeg)
    }

    @Test fun edgesUseStepsTimesStride() {
        val e = b.edges.single { it.from == "E-WS" }
        assertEquals("E-95DS", e.to); assertEquals(45.0, e.lengthM, 0.01); assertEquals(EdgeKind.HALLWAY, e.kind)
        assertTrue("wandered" in e.notes!!)
        assertEquals(14.58, b.edges.single { it.from == "R-150" }.lengthM, 0.01)
    }

    @Test fun roomIsDeadReckonedFromTheSurveyedHeading() {
        val ws = b.node("E-WS"); val room = b.node("R-150")
        val d = 35 * 0.41666666; val h = Math.toRadians(38.5)
        assertEquals(ws.x - d * kotlin.math.sin(h), room.x, 0.02); assertEquals(ws.y - d * kotlin.math.cos(h), room.y, 0.02)
        assertTrue(!room.estimated)
    }

    @Test fun anchorsFollowTheRules() {
        val a1 = b.anchors.single { it.id == "CS-A01" }; val a2 = b.anchors.single { it.id == "CS-A02" }
        assertEquals("E-WM", a1.node); assertEquals(AnchorKind.IMAGE, a1.kind); assertEquals(2.0, a1.widthM)
        assertEquals("anchors/CS/CS-A01.jpg", a1.image); assertEquals("north", a1.facing); assertEquals(2.0, a1.heightM)
        assertEquals("R-150", a2.node); assertEquals("east", a2.facing); assertEquals(1.6, a2.heightM); assertEquals("150", a2.text)
        assertTrue("AUDITORIUM 150 CORGAE" in a2.aliases)
    }

    @Test fun reportListsTheGaps() {
        val r = SurveyConverter.report(json)
        println(r)
        for (needle in listOf("elevator node on floor 1", "elevator node on floor 6", "room node R-608", "anchors on floor 6",
            "elevator timings", "stairs timings", "entrances on floor 2", "START points", "rule 5"))
            assertTrue(needle in r, "report misses '$needle'")
    }

    @Test fun draftSurvivesJsonRoundTrip() {
        assertEquals(b, BuildingLoader.fromJson(BuildingLoader.toJson(b)))
    }

    @Test fun elevatorAndStairsObservationsBecomeVerticalEdges() {
        val log = """{"session":{"building":"CS","strideM":0.7},"observations":[
          {"kind":"node","id":1,"floor":1,"nodeType":"ENTRANCE","name":"Door","outdoor":true,"snapshot":{"gps":{"lat":33.75,"lng":-84.38}}},
          {"kind":"elevator","id":2,"floor":1,"fromFloor":1,"toFloor":6,"waitSec":30,"rideSec":24,"futureField":[1,2]},
          {"kind":"elevator","id":3,"floor":1,"fromFloor":1,"toFloor":6,"waitSec":40,"rideSec":26},
          {"kind":"stairs","id":4,"floor":1,"fromFloor":1,"toFloor":2,"durationSec":20}]}"""
        val d = SurveyConverter.convert(log)
        assertEquals(setOf("E-D", "EL-1", "EL-6", "ST-1", "ST-2"), d.nodes.map { it.id }.toSet())
        val lift = d.elevators.single()
        assertEquals(35.0, lift.avgWaitSec); assertEquals(40.0, lift.worstWaitSec); assertEquals(5.0, lift.secondsPerFloor)
        assertEquals(20.0, d.stairsSecondsPerFloor)
        assertEquals(5, d.edges.single { it.kind == EdgeKind.ELEVATOR }.floors)
        assertTrue(d.nodes.filter { it.id != "E-D" }.all { it.estimated })
    }

    @Test fun verticalFieldsAreReadLeniently() {
        // Format specification names no elevator/stairs fields; accept arrivalFloor, the observation floor as departure,
        // Called/Boarded/Doors-opened timestamps, numbers as strings, and ignore junk instead of failing the log.
        val log = """{"session":{"building":"CS"},"observations":[
          {"kind":"node","id":1,"floor":1,"nodeType":"ENTRANCE","name":"Door","outdoor":true,"snapshot":{"gps":{"lat":33.75,"lng":-84.38}}},
          {"kind":"elevator","id":2,"floor":1,"arrivalFloor":"6","waitSec":"n/a",
           "calledAt":"2026-09-26T10:00:00-04:00","boardedAt":"2026-09-26T10:00:40-04:00","doorsOpenedAt":"2026-09-26T10:01:05-04:00"},
          {"kind":"elevator","id":3,"floor":1,"fromFloor":"1","toFloor":6,"waitSec":"30","rideSec":25},
          {"kind":"elevator","id":4,"floor":1},
          {"kind":"stairs","id":5,"floor":2,"toFloor":6}]}"""
        val d = SurveyConverter.convert(log)
        val lift = d.elevators.single()
        assertEquals("2 rides", lift.notes)
        assertEquals(35.0, lift.avgWaitSec); assertEquals(40.0, lift.worstWaitSec); assertEquals(5.0, lift.secondsPerFloor)
        assertEquals(setOf("EL-1", "EL-6", "ST-2", "ST-6"), d.nodes.filter { it.type != NodeType.ENTRANCE }.map { it.id }.toSet())
        assertEquals(22.0, d.stairsSecondsPerFloor) // no duration: default kept
    }

    // ---- survey 0.3, session CS-20260925-1238 (real elevator/stairs fields, 0-step walk, duplicate anchor) ----
    private val json03 = TestData.resource("CS-20260925-1238.survey.json")
    private val b03 = SurveyConverter.convert(json03)

    @Test fun v03CountsAndSession() {
        val log = SurveyLog.parse(json03)
        assertEquals("gps-walk", log.session.strideMethod)
        assertEquals(5, b03.nodes.count { it.type == NodeType.ENTRANCE })
        assertEquals(listOf(1, 1, 1, 2, 2), b03.nodes.filter { it.type == NodeType.ENTRANCE }.map { it.floor })
        assertEquals(7, b03.anchors.size, "CS-A02 was logged twice (#12, #13); one anchor")
        assertEquals(setOf("EL-1", "EL-6", "ST-1", "ST-2", "ST-6"), b03.nodes.filter { it.type == NodeType.ELEVATOR || it.type == NodeType.STAIRS }.map { it.id }.toSet())
        assertTrue("gps-walk" in b03.notes!!)
        assertEquals(b03, BuildingLoader.fromJson(BuildingLoader.toJson(b03)))
    }

    @Test fun v03ElevatorRidesAndStairDescents() {
        val lift = b03.elevators.single()
        assertEquals("2 rides", lift.notes)
        assertEquals((3.446 + 1.374) / 2, lift.avgWaitSec, 0.01); assertEquals(3.45, lift.worstWaitSec, 0.01)
        assertEquals((22.389 + 22.03) / 2 / 5, lift.secondsPerFloor, 0.01)
        assertEquals(5, b03.edges.single { it.kind == EdgeKind.ELEVATOR }.floors)
        // `floor` on a 0.3 ride is the arrival floor (6); fromFloor 1 comes from the explicit field.
        assertEquals(setOf("EL-1", "EL-6"), b03.edges.single { it.kind == EdgeKind.ELEVATOR }.let { setOf(it.from, it.to) })
        // Only descents were timed: 2 -> 1 in 20.788 s and 6 -> 1 in 60.901 s = 81.689 s over 6 floors. Up stays the default.
        assertEquals(13.61, b03.stairsDownSecondsPerFloor, 0.01)
        assertEquals(22.0, b03.stairsSecondsPerFloor)
        assertTrue("UPWARD stairs climb" in SurveyConverter.report(json03))
        // The rides hang EL-1 and EL-6 on the surveyed "elevator lobby" intersections instead of floating at indoor GPS.
        for (el in listOf("EL-1", "EL-6")) assertTrue(b03.edges.any { it.to == el && it.kind == EdgeKind.HALLWAY && it.estimated })
    }

    @Test fun v03ZeroStepWalkIsEstimatedNotZero() {
        val e = b03.edges.single { it.notes!!.startsWith("obs #33,") }
        assertTrue(e.estimated); assertTrue("0 steps" in e.notes!!)
        assertEquals(17.0, e.lengthM, 0.5) // GPS straight line between two indoor fixes of +-6.5 and +-8 m
        val a = b03.node(e.from); val c = b03.node(e.to)
        assertTrue(kotlin.math.hypot(a.x - c.x, a.y - c.y) > 1.0, "a 0-step walk must not put its ends on top of each other")
    }

    @Test fun v03TurnsInsideWalksAreFlagged() {
        val flagged = b03.edges.filter { "probably a turn" in (it.notes ?: "") }.map { it.notes!!.substringBefore(",") }.toSet()
        assertEquals(setOf("obs #2", "obs #6", "obs #24", "obs #33", "obs #37"), flagged)
        assertTrue(b03.edges.none { "obs #35," in (it.notes ?: "") && "probably a turn" in it.notes!! }) // 44.8° is under the limit
    }

    @Test fun v03AnchorsUseWidthEstimatedAndRejectAbsurdOffsets() {
        val a01 = b03.anchors.single { it.id == "CS-A01" }; val a05 = b03.anchors.single { it.id == "CS-A05" }
        assertTrue(!a01.estimated); assertTrue(a05.estimated); assertTrue("width estimated" in a05.notes!!)
        val a02 = b03.anchors.single { it.id == "CS-A02" }; assertTrue("duplicate obs #13 ignored" in a02.notes!!)
        val a04 = b03.anchors.single { it.id == "CS-A04" }; val n = b03.node(a04.node)
        assertEquals(n.x, a04.x, 1e-9); assertEquals(n.y, a04.y, 1e-9) // offsetFromNodeM 300 ignored
        assertTrue("not believable" in a04.notes!!)
    }

    @Test fun v03DraftHasNoCoincidentNodes() {
        assertEquals(emptyList(), com.campusmaps.data.BuildingValidator.validate(b03).filter { it.rule == 9 })
    }

    @Test fun v03FromFloorFallbackIgnoresTheArrivalFloor() {
        // A 0.3 ride saved on its arrival floor with no fromFloor cannot say where it started: skipped, not a 6 -> 6 edge.
        val log = """{"session":{"building":"CS"},"observations":[
          {"kind":"node","id":1,"floor":1,"nodeType":"ENTRANCE","name":"Door","outdoor":true,"snapshot":{"gps":{"lat":33.75,"lng":-84.38}}},
          {"kind":"elevator","id":2,"floor":6,"toFloor":6,"waitSec":3,"rideSec":20}]}"""
        val d = SurveyConverter.convert(log)
        assertTrue(d.elevators.isEmpty()); assertTrue(d.nodes.none { it.type == NodeType.ELEVATOR })
    }

    @Test fun shortNames() {
        assertEquals("95DS", SurveyConverter.shortName("95 Decatur Street entrance"))
        assertEquals("WM", SurveyConverter.shortName("Walters main"))
        assertEquals("LM", SurveyConverter.shortName("LibSo main entrance"))
    }
}
