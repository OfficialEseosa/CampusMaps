package com.campusmaps.outdoor

import com.campusmaps.data.Geo
import com.campusmaps.data.model.Building
import com.campusmaps.routing.InstructionType
import com.campusmaps.routing.Prefs
import com.campusmaps.routing.Router
import com.campusmaps.routing.Start
import java.time.LocalDateTime
import kotlin.math.ceil
import com.campusmaps.routing.RouteOption as CoreOption

// Leg 1 of the journey: from where the student stands outside to the recommended entrance of the building.
// Everything in this file is plain Kotlin (no Android), so it is tested on the JVM.

data class LatLngPoint(val lat: Double, val lng: Double)

// Where the student is, from the fused location provider.
data class UserFix(val lat: Double, val lng: Double, val accuracyM: Float) {
    val point: LatLngPoint get() = LatLngPoint(lat, lng)
}

// The entrance core recommends for this destination from this position (first core RouteOption).
data class EntrancePlan(
    val buildingId: String,
    val buildingName: String,
    val destinationId: String,
    val destinationName: String,
    val destinationFloor: Int,
    val entranceId: String,
    val entranceName: String,
    val entranceFloor: Int,
    val entrance: LatLngPoint,
    // True when the entrance has no lat/lng in the building file and the building origin is used instead.
    val approximate: Boolean,
    // Core's first instruction after going in, e.g. "Take the elevator to floor 6".
    val firstIndoorInstruction: String?,
    val core: CoreOption,
)

enum class RouteSource { STRAIGHT_LINE, DIRECTIONS }

// The line drawn on the map and the numbers in the sheet.
data class OutdoorRoute(
    val points: List<LatLngPoint>,
    val distanceM: Double,
    val minutes: Int,
    val streetSteps: List<String>,
    val source: RouteSource,
    // Google's maneuvers with where each one ends; S2 follows them by GPS (outdoor/StreetSteps.kt). Empty on a straight line.
    val streetLegs: List<StreetStep> = emptyList(),
)

// Which arrow a street step gets (from Google's maneuver: TURN_LEFT, turn-slight-right, STRAIGHT, DEPART, ...).
enum class StreetTurn {
    LEFT, RIGHT, STRAIGHT;

    companion object {
        fun fromManeuver(maneuver: String?): StreetTurn {
            val m = maneuver.orEmpty().uppercase().replace('-', '_')
            return when {
                "LEFT" in m -> LEFT
                "RIGHT" in m -> RIGHT
                else -> STRAIGHT
            }
        }
    }
}

// One Google walking maneuver: plain text, its arrow, where it ends and how long it is.
data class StreetStep(val text: String, val turn: StreetTurn, val end: LatLngPoint, val distanceM: Double)

object OutdoorRoutes {
    // Walking speed used for the outdoor estimate (docs: 1.3 m/s).
    const val WALK_MPS = 1.3

    fun distanceM(a: LatLngPoint, b: LatLngPoint): Double = Geo.haversineM(a.lat, a.lng, b.lat, b.lng)

    // Whole minutes, rounded up, at least 1.
    fun walkingMinutes(distanceM: Double): Int = ceil(distanceM / WALK_MPS / 60.0).toInt().coerceAtLeast(1)

    // No key, offline, or Directions failed: a straight line to the entrance.
    fun straight(from: LatLngPoint, to: LatLngPoint): OutdoorRoute {
        val d = distanceM(from, to)
        return OutdoorRoute(listOf(from, to), d, walkingMinutes(d), emptyList(), RouteSource.STRAIGHT_LINE)
    }

    // Numbered steps in the sheet: street steps (or one "Walk to" step), going in, then core's first indoor step.
    fun sheetSteps(route: OutdoorRoute?, plan: EntrancePlan): List<String> = buildList {
        val door = shortName(plan.entranceName)
        if (route != null && route.streetSteps.isNotEmpty()) addAll(route.streetSteps) else add("Walk to the $door")
        add("Enter ${plan.buildingName} at the $door")
        plan.firstIndoorInstruction?.let { add(it) }
    }

    // "Library South entrance (floor 2)" -> "Library South entrance" (the floor has its own tile).
    fun shortName(name: String): String = name.replace(Regex("\\s*\\(.*\\)\\s*$"), "").trim()

    // Asks core which entrance to use from (lat, lng). Null when core finds no route (for example every door locked).
    fun plan(building: Building, destinationId: String, from: LatLngPoint, now: LocalDateTime, avoidStairs: Boolean, hasCard: Boolean = false): EntrancePlan? {
        val core = building.core
        val destination = core.nodeOrNull(destinationId) ?: return null
        val option = Router.route(core, Start.Outside(from.lat, from.lng), destinationId, Prefs(avoidStairs, now, hasCard)).firstOrNull()
            ?: return null
        val entranceId = option.entrance ?: return null
        val entrance = core.nodeOrNull(entranceId) ?: return null
        val lat = entrance.lat
        val lng = entrance.lng
        val approximate = lat == null || lng == null
        val point = if (approximate) LatLngPoint(core.origin.lat, core.origin.lng) else LatLngPoint(lat!!, lng!!)
        val indoor = option.instructions.firstOrNull {
            it.type != InstructionType.START && it.type != InstructionType.ENTRANCE && it.type != InstructionType.LOCKED_NOTICE
        }?.text
        return EntrancePlan(
            buildingId = building.id,
            buildingName = building.name,
            destinationId = destinationId,
            destinationName = destination.name,
            destinationFloor = destination.floor,
            entranceId = entranceId,
            entranceName = option.entranceName ?: entrance.name,
            entranceFloor = option.entranceFloor ?: entrance.floor,
            entrance = point,
            approximate = approximate,
            firstIndoorInstruction = indoor,
            core = option,
        )
    }

    // Distance from (lat, lng) to the nearest building origin, for picking the home screen.
    fun nearestBuildingM(buildings: List<Building>, from: LatLngPoint): Double =
        buildings.minOfOrNull { distanceM(from, LatLngPoint(it.core.origin.lat, it.core.origin.lng)) } ?: Double.MAX_VALUE

    // Explore is home when no fix, or further than [HOME_RADIUS_M] from every building (and not in demo mode).
    const val HOME_RADIUS_M = 150.0
    fun exploreIsHome(buildings: List<Building>, fix: LatLngPoint?, demoMode: Boolean): Boolean =
        !demoMode && (fix == null || nearestBuildingM(buildings, fix) > HOME_RADIUS_M)

    // Google's encoded polyline format (Directions overview_polyline).
    fun decodePolyline(encoded: String): List<LatLngPoint> {
        val out = mutableListOf<LatLngPoint>()
        var i = 0; var lat = 0; var lng = 0
        while (i < encoded.length) {
            for (axis in 0..1) {
                var result = 0; var shift = 0; var b: Int
                do {
                    b = encoded[i++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20 && i < encoded.length)
                val delta = if (result and 1 != 0) (result shr 1).inv() else result shr 1
                if (axis == 0) lat += delta else lng += delta
            }
            out += LatLngPoint(lat / 1e5, lng / 1e5)
        }
        return out
    }
}
