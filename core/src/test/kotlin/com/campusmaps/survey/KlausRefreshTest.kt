package com.campusmaps.survey

import com.campusmaps.TestData
import com.campusmaps.data.AnchorKind
import com.campusmaps.data.BuildingLoader
import com.campusmaps.data.BuildingValidator
import com.campusmaps.data.NodeType
import com.campusmaps.data.Severity
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression for the converter path the Klaus refresh uses (docs/19 "Klaus refresh, step by step"): a survey 0.3 export
 * zip in, a building file out, hand-tuned fields kept. There is no Klaus log yet, so the real CS 0.3 log stands in.
 */
class KlausRefreshTest {
    private val cs03 = TestData.resource("CS-20260925-1238.survey.json")

    /** Same layout as a survey export: `<session>/survey.json` next to photos and a README. */
    private fun zipOf(json: String, folder: String): File {
        val dir = Files.createTempDirectory("survey").toFile()
        val zip = File(dir, "$folder.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("$folder/README.txt")); z.write("not the log".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("$folder/survey.json")); z.write(json.toByteArray()); z.closeEntry()
        }
        return zip
    }

    @Test fun csExportZipConvertsEndToEndThroughConvertMain() {
        val zip = zipOf(cs03, "CS-20260925-1238")
        val out = File(zip.parentFile, "draft-CS.json")
        ConvertMain.main(arrayOf(zip.path, out.path, "--keep", TestData.buildingFile("CS").path))
        val d = BuildingLoader.fromJson(out.readText())
        assertEquals("CS", d.code)
        assertEquals(SurveyConverter.convert(cs03), d.copy(startPoints = emptyList(), demoDestinations = emptyList()))
        assertEquals(listOf("P1", "P2"), d.startPoints.map { it.id }) // the survey has no START node: the hand-made P1, P2 stay
        assertEquals(listOf("R-150"), d.demoDestinations)            // the survey has no 608 room node (docs/19)
        // Rule 5 fails on the raw draft: the survey left legs unwalked, rebuilt by hand from the videos (docs/19). Nothing else may.
        assertEquals(emptyList(), BuildingValidator.validate(d).filter { it.severity == Severity.ERROR && it.rule != 5 })
        assertTrue(BuildingValidator.validate(d).any { it.rule == 5 })
    }

    @Test fun aKlausLogKeepsTheDemoIdsAndDestination() {
        // The CS log relabelled as a Klaus session, with two nodes named the way the Klaus guide asks (S1, the demo room number).
        val log = SurveyLog.parse(cs03)
        val kl = log.copy(session = log.session.copy(building = "KL"), observations = log.observations.map {
            when {
                it.kind == "node" && it.name == "Walters corner to main" -> it.copy(nodeType = "WAYPOINT", name = "S1")
                it.kind == "node" && it.name == "Room 150" -> it.copy(name = "Room 1116")
                else -> it
            }
        })
        val draft = SurveyConverter.convert(kl)
        assertEquals("KL", draft.code)
        assertTrue(draft.anchors.filter { it.kind == AnchorKind.IMAGE }.all { it.image!!.startsWith("anchors/KL/") })
        val r = HandTuned.keep(draft, TestData.load("KL"))
        val b = r.building
        assertEquals(NodeType.WAYPOINT, b.nodeOrNull("S1")?.type, r.log.joinToString("\n"))
        assertEquals(listOf("R-1116"), b.demoDestinations)
        assertTrue(r.log.any { "renamed" in it && "to S1" in it })
        assertTrue(r.log.any { "old node S2" in it }, "missing old ids are reported")
        assertEquals(emptyList(), BuildingValidator.validate(b).filter { it.rule in 0..2 })
    }

    @Test fun accessWindowsSurviveARefresh() {
        val cse = TestData.load("CSE")
        val draft = cse.copy(nodes = cse.nodes.map { it.copy(access = null) }, demoDestinations = emptyList())
        val b = HandTuned.keep(draft, cse).building
        assertEquals(cse.node("E-MAIN").access, b.node("E-MAIN").access)
        assertEquals(listOf("R-220"), b.demoDestinations)
    }
}
