package com.campusmaps.data

import com.campusmaps.TestData
import com.campusmaps.routing.Prefs
import com.campusmaps.routing.Router
import com.campusmaps.routing.Start
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BuildingPatchTest {
    private val cs = TestData.load("CS")

    /** Room 612 two metres east of H6 on floor 6, joined to H6: the field test from the editor brief. */
    private fun room612(): Pair<Building, String> {
        val h6 = cs.node("H6")
        val (b, id) = BuildingEdits.addNode(cs, EditKind.ROOM, "Room 612", 6, h6.x + 2.0, h6.y)
        return BuildingEdits.connect(b, "H6", id)!! to id
    }

    @Test fun idRule() {
        assertEquals("H12", BuildingEdits.newId(cs, EditKind.HALLWAY)) // CS has H1..H11
        assertEquals("D1", BuildingEdits.newId(cs, EditKind.DOOR))
        assertEquals("R-612", BuildingEdits.newId(cs, EditKind.ROOM, "Room 612"))
        assertEquals("R-609", BuildingEdits.newId(cs, EditKind.ROOM, "Lab")) // highest R-<n> is R-608
        assertEquals("R-609", BuildingEdits.newId(cs, EditKind.ROOM, "Room 608")) // taken
        val (b, h) = BuildingEdits.addNode(cs, EditKind.HALLWAY, "", 6, 0.0, 0.0)
        assertEquals("H12", h); assertEquals("H13", BuildingEdits.newId(b, EditKind.HALLWAY))
    }

    @Test fun addConnectAndDiffRoundTrip() {
        val (edited, id) = room612()
        assertEquals("R-612", id)
        val e = edited.edges.last()
        assertEquals(EdgeKind.HALLWAY, e.kind); assertEquals(2.0, e.lengthM)
        val patch = BuildingPatch.diff(cs, edited, note = "field fix")
        assertEquals(listOf("R-612"), patch.addedNodes.map { it.id })
        assertEquals(1, patch.addedEdges.size); assertTrue(patch.removedEdges.isEmpty())
        val back = BuildingPatch.fromJson(patch.toJson())
        assertEquals(patch, back)
        val merged = BuildingPatch.apply(cs, back)
        assertEquals(edited.nodes, merged.nodes); assertEquals(edited.edges, merged.edges)
        assertTrue(BuildingValidator.validate(merged).none { it.severity == Severity.ERROR }, BuildingValidator.validate(merged).toString())
        val options = Router.route(merged, cs.startPoints.single { it.id == "P1" }.let { Start.Outside(it.lat, it.lng) }, "R-612",
            Prefs(now = TestData.saturday(14)))
        assertTrue(options.isNotEmpty())
    }

    @Test fun changeMoveRenameAndRemove() {
        var b = BuildingEdits.update(cs, "R-608") { it.copy(name = "Room 608 (Lab)", floor = 6) }
        b = BuildingEdits.move(b, "H11", 15.0, -33.0)
        b = BuildingEdits.disconnect(b, "H1", "H2")
        val (withDoor, d) = BuildingEdits.addNode(b, EditKind.DOOR, "", 1, 1.0, 1.0)
        b = BuildingEdits.connect(withDoor, d, "H1")!!
        assertEquals(EdgeKind.DOOR, b.edges.last().kind)
        b = BuildingEdits.delete(b, "R-150")
        val patch = BuildingPatch.diff(cs, b)
        assertEquals(listOf("R-150"), patch.removedNodes)
        assertEquals(setOf("R-608", "H11"), patch.changedNodes.map { it.id }.toSet())
        assertEquals("Room 608 (Lab)", patch.changedNodes.single { it.id == "R-608" }.name)
        assertNull(patch.changedNodes.single { it.id == "R-608" }.x)
        assertEquals(listOf(EdgeRef("H1", "H2")), patch.removedEdges)
        val merged = BuildingPatch.apply(cs, BuildingPatch.fromJson(patch.toJson()))
        assertEquals(b.nodes, merged.nodes); assertEquals(b.edges.toSet(), merged.edges.toSet())
        assertTrue(merged.demoDestinations.none { it == "R-150" }); assertTrue(merged.anchors.none { it.node == "R-150" })
    }

    @Test fun badPatchIsSkipped() {
        val bad = listOf(
            BuildingPatch("CS", removedNodes = listOf("NOPE")),
            BuildingPatch("CS", changedNodes = listOf(NodeChange("NOPE", name = "x"))),
            BuildingPatch("CS", addedNodes = listOf(cs.node("H6"))),
            BuildingPatch("CS", addedEdges = listOf(Edge("H6", "NOPE", 1.0))),
            BuildingPatch("CS", removedEdges = listOf(EdgeRef("H6", "R-150"))),
            BuildingPatch("KL"),
        )
        for (p in bad) {
            assertFailsWith<PatchException> { BuildingPatch.apply(cs, p) }
            val (b, why) = BuildingPatch.applyOrSkip(cs, p)
            assertSame(cs, b); assertNotNull(why)
        }
        // Once the merged file is committed, the old patch no longer fits: it is skipped and the asset (with the room) wins.
        val (edited, _) = room612()
        val (b, why) = BuildingPatch.applyOrSkip(edited, BuildingPatch.diff(cs, edited))
        assertSame(edited, b); assertNotNull(why)
    }

    @Test fun emptyPatchJsonIsTiny() {
        val p = BuildingPatch("CS")
        assertTrue(p.isEmpty); assertEquals(p, BuildingPatch.fromJson("""{"building":"CS"}"""))
    }

    /** Checks a file exported from the phone: EXPORTED_BUILDING=path/to/CS.json ./gradlew :core:test --tests '*BuildingPatchTest*'. */
    @Test fun exportedFileLoads() {
        val path = System.getenv("EXPORTED_BUILDING") ?: return
        val b = BuildingLoader.fromJson(File(path).readText())
        val problems = BuildingValidator.validate(b)
        println("exported ${b.code}: ${b.nodes.size} nodes, ${b.edges.size} edges; " +
            "${problems.count { it.severity == Severity.ERROR }} errors, ${problems.count { it.severity == Severity.WARN }} warnings")
        problems.forEach { println("  $it") }
        assertTrue(problems.none { it.severity == Severity.ERROR && it.rule != 7 })
    }
}
