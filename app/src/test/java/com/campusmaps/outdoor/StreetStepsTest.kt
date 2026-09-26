package com.campusmaps.outdoor

import com.campusmaps.data.campus.TestBuildings
import com.campusmaps.route.CoreRouter
import com.campusmaps.route.FloorChange
import com.campusmaps.route.Route
import com.campusmaps.route.RoutePlan
import com.campusmaps.route.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class StreetStepsTest {

    private val cs = TestBuildings.cs
    private val route: Route = (CoreRouter().plan(cs, "P1", "R-608", LocalDateTime.of(2026, 9, 25, 12, 0), avoidStairs = false) as RoutePlan.Options)
        .options.first { it.method == FloorChange.ELEVATOR }.route
    private val door = LatLngPoint(33.7528343, -84.38760369)
    private val streets = listOf(
        StreetStep("Head west on John Wesley Dobbs Ave NE toward Courtland St NE", StreetTurn.STRAIGHT, LatLngPoint(33.7560, -84.3860), 200.0),
        StreetStep("Turn left onto Peachtree Center Ave NE", StreetTurn.LEFT, LatLngPoint(33.7540, -84.3870), 300.0),
        StreetStep("Turn right. Destination will be on the left", StreetTurn.RIGHT, door, 100.0),
    )

    @Test fun routesApiStepsKeepManeuverEndAndDistance() {
        val body = """{"routes":[{"distanceMeters":600,"polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC"},"legs":[{"steps":[
            {"distanceMeters":200,"endLocation":{"latLng":{"latitude":33.756,"longitude":-84.386}},
             "navigationInstruction":{"instructions":"Head west on John Wesley Dobbs Ave NE\ntoward Courtland St NE"}},
            {"distanceMeters":300,"endLocation":{"latLng":{"latitude":33.754,"longitude":-84.387}},
             "navigationInstruction":{"maneuver":"TURN_LEFT","instructions":"Turn left onto Peachtree Center Ave NE"}},
            {"distanceMeters":100,"endLocation":{"latLng":{"latitude":33.7528,"longitude":-84.3876}},
             "navigationInstruction":{"maneuver":"TURN_SLIGHT_RIGHT","instructions":"Turn <b>right</b>"}},
            {"distanceMeters":5}]}]}]}"""
        val steps = DirectionsClient.parseRoutesStreetSteps(body)
        assertEquals(3, steps.size)
        assertEquals("Head west on John Wesley Dobbs Ave NE toward Courtland St NE", steps[0].text)
        assertEquals(listOf(StreetTurn.STRAIGHT, StreetTurn.LEFT, StreetTurn.RIGHT), steps.map { it.turn })
        assertEquals("Turn right", steps[2].text)
        assertEquals(LatLngPoint(33.754, -84.387), steps[1].end)
        assertEquals(300.0, steps[1].distanceM, 0.0)
        assertEquals(steps, DirectionsClient.parseRoutes(body)!!.streetLegs)
    }

    @Test fun legacyStepsStripHtml() {
        val body = """{"status":"OK","routes":[{"overview_polyline":{"points":"_p~iF~ps|U_ulLnnqC"},"legs":[{"distance":{"value":260},"steps":[
            {"html_instructions":"Turn <b>left</b> onto <b>Gilmer St</b><div style=\"font-size:0.9em\">Destination will be on the right</div>",
             "maneuver":"turn-left","end_location":{"lat":33.753,"lng":-84.388},"distance":{"value":260}}]}]}]}"""
        val s = DirectionsClient.parseLegacyStreetSteps(body).single()
        assertEquals("Turn left onto Gilmer St. Destination will be on the right", s.text)
        assertEquals(StreetTurn.LEFT, s.turn)
        assertEquals(LatLngPoint(33.753, -84.388), s.end)
        assertTrue(DirectionsClient.parseLegacyStreetSteps("""{"status":"ZERO_RESULTS"}""").isEmpty())
    }

    @Test fun blockBreaksBecomeSentences() {
        assertEquals("Head east. Take the stairs", DirectionsClient.plainText("Head <b>east</b><div>Take the stairs</div>"))
        assertEquals("Head east. Take the stairs", DirectionsClient.plainText("Head east\nTake the stairs"))
        assertEquals("Head east. Take the stairs", DirectionsClient.plainText("Head east<br/>Take the stairs"))
        assertEquals("Continue onto Decatur St. Destination will be on the left",
            DirectionsClient.plainText("Continue onto Decatur St.\nDestination will be on the left"))
        assertEquals("Head west on Dobbs Ave NE toward Courtland St NE", DirectionsClient.plainText("Head west on Dobbs Ave NE\ntoward Courtland St NE"))
        assertEquals("Turn right", DirectionsClient.plainText("<div>Turn right</div>"))
        // Routes API text (newline between the sentences) reaches both the sheet and S2 with the full stop.
        val body = """{"routes":[{"distanceMeters":260,"polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC"},"legs":[{"steps":[
            {"navigationInstruction":{"maneuver":"TURN_RIGHT","instructions":"Turn right onto Central Ave SW\nDestination will be on the left"},
             "endLocation":{"latLng":{"latitude":33.753,"longitude":-84.388}},"distanceMeters":18}]}]}]}"""
        val r = DirectionsClient.parseRoutes(body)!!
        assertEquals("Turn right onto Central Ave SW. Destination will be on the left", r.streetLegs.single().text)
        assertEquals(listOf("Turn right onto Central Ave SW. Destination will be on the left"), r.streetSteps)
    }

    @Test fun maneuverMapsToArrow() {
        assertEquals(StreetTurn.LEFT, StreetTurn.fromManeuver("TURN_SHARP_LEFT"))
        assertEquals(StreetTurn.RIGHT, StreetTurn.fromManeuver("turn-slight-right"))
        assertEquals(StreetTurn.STRAIGHT, StreetTurn.fromManeuver("DEPART"))
        assertEquals(StreetTurn.STRAIGHT, StreetTurn.fromManeuver(null))
    }

    @Test fun googleStepsThenEnterThenIndoorUnchanged() {
        val r = StreetSteps.apply(route, streets, door, "Classroom South", "Library South entrance (floor 2)")
        val indoor = route.steps.drop(1)
        assertEquals(streets.size + 1 + indoor.size, r.steps.size)
        assertEquals(listOf(StepKind.STREET_STRAIGHT, StepKind.STREET_LEFT, StepKind.STREET_RIGHT), r.steps.take(3).map { it.kind })
        assertEquals(streets.map { it.text }, r.steps.take(3).map { it.text })
        assertEquals(streets.map { it.end }, r.steps.take(3).map { it.outdoorEnd })
        val enter = r.steps[3]
        assertEquals(StepKind.WALK_TO_ENTRANCE, enter.kind)
        assertEquals("Enter Classroom South at the Library South entrance", enter.text)
        assertEquals(door, enter.outdoorEnd)
        assertEquals(indoor, r.steps.drop(4))
        assertTrue(r.steps.drop(4).all { it.outdoorEnd == null })
        // Without GPS the walker still moves through them in order, the Enter step last.
        val done = r.steps.take(4).map { it.completeAtM }
        assertEquals(done.sorted(), done)
        assertTrue(done[2] < done[3])
        assertEquals(route.points, r.points)
    }

    @Test fun withoutDirectionsKeepsTheSingleWalkStep() {
        val r = StreetSteps.apply(route, emptyList(), door, "Classroom South", "Library South entrance")
        assertEquals(route.steps.size, r.steps.size)
        assertEquals(route.steps[0].text, r.steps[0].text)
        assertEquals(StepKind.WALK_TO_ENTRANCE, r.steps[0].kind)
        assertEquals(door, r.steps[0].outdoorEnd)
        assertNull(r.steps[1].outdoorEnd)
    }
}
