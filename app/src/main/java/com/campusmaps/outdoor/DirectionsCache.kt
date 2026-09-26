package com.campusmaps.outdoor

import kotlin.math.cos
import kotlin.math.roundToLong

// Keeps the last Directions answer (or failure) per entrance so a new GPS fix does not trigger a new request.
// A new request is needed only when the entrance changed or the student moved more than MOVE_M from the
// (15 m grid rounded) point of the last request.
class DirectionsCache(private val moveM: Double = MOVE_M) {
    private var entranceId: String? = null
    private var requestedAt: LatLngPoint? = null
    private var route: OutdoorRoute? = null

    // True when the caller should call Directions; false means use cached() (which may be null after a failure).
    fun shouldRequest(entranceId: String, from: LatLngPoint): Boolean {
        val at = requestedAt ?: return true
        if (entranceId != this.entranceId) return true
        return OutdoorRoutes.distanceM(at, snap(from)) > moveM
    }

    fun cached(): OutdoorRoute? = route

    fun store(entranceId: String, from: LatLngPoint, route: OutdoorRoute?) {
        this.entranceId = entranceId
        this.requestedAt = snap(from)
        this.route = route
    }

    fun clear() { entranceId = null; requestedAt = null; route = null }

    companion object {
        const val MOVE_M = 25.0
        const val GRID_M = 15.0
        private const val M_PER_DEG_LAT = 111_320.0

        // Rounds a position to a grid of about 15 m so GPS jitter maps to the same key.
        fun snap(p: LatLngPoint): LatLngPoint {
            val latStep = GRID_M / M_PER_DEG_LAT
            val lngStep = GRID_M / (M_PER_DEG_LAT * cos(Math.toRadians(p.lat)).coerceAtLeast(0.01))
            return LatLngPoint((p.lat / latStep).roundToLong() * latStep, (p.lng / lngStep).roundToLong() * lngStep)
        }
    }
}
