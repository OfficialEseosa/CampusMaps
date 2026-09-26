package com.campusmaps.outdoor

import com.campusmaps.route.Route
import com.campusmaps.route.RouteStep
import com.campusmaps.route.StepKind

// S2 from the Explore map: the outdoor leg follows Google's walking maneuvers, one RouteStep each, then
// "Enter <building> at the <entrance>", then core's indoor steps unchanged. Plain Kotlin, tested on the JVM.
object StreetSteps {

    // Share of the outdoor leg the fake walker covers before the last street step is done, so the "Enter" step
    // still gets its own moment when there is no GPS (the walker then advances the steps, not GPS).
    private const val STREET_SHARE = 0.9

    /**
     * [route] with its "Walk to <entrance>" step replaced by [streets] plus an "Enter" step. Every outdoor step carries
     * its end point (RouteStep.outdoorEnd), so guidance completes it by GPS. Without [streets] the single "Walk to" step
     * stays (text unchanged) and only gets the entrance as its end point. A route that does not start outside is returned as is.
     */
    fun apply(route: Route, streets: List<StreetStep>, entrance: LatLngPoint, buildingName: String, entranceName: String): Route {
        val w = route.steps.indexOfFirst { it.kind == StepKind.WALK_TO_ENTRANCE }
        if (w < 0) return route
        val walk = route.steps[w]
        if (streets.isEmpty()) {
            return route.copy(steps = route.steps.toMutableList().also { it[w] = walk.copy(outdoorEnd = entrance) })
        }
        val legM = walk.completeAtM
        val total = streets.sumOf { it.distanceM.coerceAtLeast(0.0) }
        var sum = 0.0
        val outdoor = streets.mapIndexed { i, s ->
            sum += s.distanceM.coerceAtLeast(0.0)
            val share = if (total > 0) sum / total else (i + 1).toDouble() / streets.size
            RouteStep(
                kind = when (s.turn) {
                    StreetTurn.LEFT -> StepKind.STREET_LEFT
                    StreetTurn.RIGHT -> StepKind.STREET_RIGHT
                    StreetTurn.STRAIGHT -> StepKind.STREET_STRAIGHT
                },
                text = s.text,
                startIndex = walk.startIndex,
                completeAtM = legM * share * STREET_SHARE,
                completeFloor = walk.completeFloor,
                place = s.text,
                outdoorEnd = s.end,
            )
        }
        val enter = walk.copy(
            text = "Enter $buildingName at the ${OutdoorRoutes.shortName(entranceName)}",
            outdoorEnd = entrance,
        )
        val steps = route.steps.subList(0, w) + outdoor + enter + route.steps.subList(w + 1, route.steps.size)
        return route.copy(steps = steps)
    }
}
