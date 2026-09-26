package com.campusmaps.data

import com.campusmaps.TestData
import com.campusmaps.routing.Prefs
import com.campusmaps.routing.Router
import com.campusmaps.routing.Start
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The full validator on the three real files, as the app runs it (rule 7 against the real asset folder) plus rule 8 on the demo
 * routes the router actually picks. Prints one line per problem and the counts (docs/19 "Validator counts").
 */
class ValidatorReportTest {
    private val daytime = Prefs(now = TestData.saturday(14))

    /** Node ids of the top route option (the router's virtual outdoor start is dropped). */
    private fun top(b: Building, start: Start, to: String): List<String> =
        Router.route(b, start, to, daytime).first().nodes.filter { b.nodeOrNull(it) != null }

    private fun demoRoutes(b: Building): List<List<String>> = when (b.code) {
        "KL" -> listOf(top(b, Start.AtNode("S1"), "R-1116W"), top(b, Start.AtNode("S2"), "R-1116W"))
        "CS" -> b.startPoints.map { p -> top(b, Start.Outside(p.lat, p.lng), "R-608") }
        "CSE" -> listOf(top(b, Start.AtNode("E-MAIN"), "R-220"))
        else -> emptyList()
    }

    private fun report(code: String): List<Problem> {
        val b = TestData.load(code)
        val assets = TestData.buildingFile(code).parentFile.parentFile
        val problems = BuildingValidator.validate(b, imageExists = { File(assets, it).isFile }, demoRoutes = demoRoutes(b))
        println("== $code: ${problems.count { it.severity == Severity.ERROR }} errors, ${problems.count { it.severity == Severity.WARN }} warnings, " +
            "${problems.count { it.severity == Severity.INFO }} info")
        problems.forEach { println("  $it") }
        return problems
    }

    /**
     * Known and documented (docs/19): P2's 608 route walks the 30 m main hallway H1-H2 with no sign logged on it. Not papered over
     * with an invented anchor; the next CS visit adds one (a room plaque half way along).
     */
    private val known = setOf("CS rule 8: route E-WM->R-608: 45.4 m without an anchor between E-WM and H2")

    @Test fun noErrorsOnTheRealFilesWithImagesAndDemoRoutes() {
        val errors = listOf("KL", "CS", "CSE").flatMap { code -> report(code).filter { it.severity == Severity.ERROR }.map { "$code $it" } }
        assertEquals(emptyList(), errors - known)
    }

    @Test fun csLibrarySouthTo608RouteHasAnAnchorEvery20m() {
        val cs = TestData.load("CS")
        val p1 = cs.startPoints.single { it.id == "P1" }
        val route = top(cs, Start.Outside(p1.lat, p1.lng), "R-608")
        assertEquals("E-LM2", route.first())
        assertEquals(emptyList(), BuildingValidator.checkAnchorSpacing(cs, route))
        // Every elevator lobby has an anchor now.
        for (lobby in listOf("H9", "H5", "H6")) kotlin.test.assertTrue(cs.anchors.any { it.node == lobby }, "no anchor at $lobby")
    }

    @Test fun klPendingPhotosAreWarningsNotErrors() {
        val kl = TestData.load("KL")
        val r7 = BuildingValidator.validate(kl, imageExists = { false }).filter { it.rule == 7 }
        assertEquals(setOf("KL-A05"), kl.anchors.filter { it.imagePending }.map { it.id }.toSet())
        val pending = r7.filter { "KL-A05" in it.message }
        kotlin.test.assertTrue(pending.isNotEmpty() && pending.all { it.severity == Severity.WARN }, "$r7")
        // KL-A01 has its photo now: a missing file is an ERROR again.
        kotlin.test.assertTrue(r7.any { "KL-A01" in it.message && it.severity == Severity.ERROR }, "$r7")
    }

    @Test fun everyOutdoorEntranceHasGeoForTheOutdoorLeg() {
        for (code in listOf("KL", "CS", "CSE")) {
            val b = TestData.load(code)
            assertEquals(emptyList(), BuildingValidator.validate(b).filter { it.rule == 11 }, code)
            for (e in b.nodes.filter { it.isOutdoorEntrance }) {
                val walkIn = e.walkInHeadingDeg!!
                kotlin.test.assertTrue(walkIn >= 0.0 && walkIn < 360.0 && kotlin.math.abs(((walkIn - e.headingDeg!!) % 360 + 360) % 360 - 180) < 1e-9, "$code ${e.id}")
            }
        }
        val noGeo = TestData.load("KL").let { k -> k.copy(nodes = k.nodes.map { if (it.id == "E-RWD") it.copy(headingDeg = null) else it }) }
        val w = BuildingValidator.validate(noGeo).single { it.rule == 11 }
        assertEquals(Severity.WARN, w.severity); kotlin.test.assertTrue("E-RWD" in w.message && "headingDeg" in w.message)
    }
}
