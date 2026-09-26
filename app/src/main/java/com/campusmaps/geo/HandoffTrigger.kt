package com.campusmaps.geo

import com.campusmaps.data.Geo

/** A phone location fix (FusedLocation or a replay). [timeMs] is the fix's own wall-clock time (Location.time). */
data class LocationFix(val lat: Double, val lng: Double, val accuracyM: Double, val timeMs: Long)

/** A latitude and longitude, for entrances. */
data class LatLng(val lat: Double, val lng: Double)

/**
 * The 40 m trigger (LEG 2): when the phone's FusedLocation distance to the chosen entrance drops below [TRIGGER_M],
 * fire once, so the map can show "Almost there. Point your camera ahead" and hand off to S2.
 *
 * Fires once per route. Re-arms when a new route starts ([reset]) or when the user goes back above [REARM_M].
 * Fixes older than [STALE_MS] (against the caller's `nowMs`) are ignored. Never reads the clock itself.
 */
class HandoffTrigger(
    private val triggerM: Double = TRIGGER_M,
    private val rearmM: Double = REARM_M,
    private val staleMs: Long = STALE_MS,
) {
    companion object {
        /** Distance to the entrance that starts the hand-off. Tunable. */
        const val TRIGGER_M = 40.0
        /** Going back above this re-arms the trigger (hysteresis, so GPS jitter around 40 m does not fire twice). */
        const val REARM_M = 60.0
        /** Fixes older than this are ignored. */
        const val STALE_MS = 10_000L
    }

    var entrance: LatLng? = null
        private set
    var armed: Boolean = true
        private set
    /** Distance to the entrance from the last fresh fix, metres. */
    var lastDistanceM: Double? = null
        private set

    /** A new route (or a new chosen entrance): re-arm. */
    fun reset(entrance: LatLng?) {
        this.entrance = entrance
        armed = true
        lastDistanceM = null
    }

    /** Feed one fix. Returns true exactly when the trigger fires. */
    fun onFix(fix: LocationFix, nowMs: Long): Boolean {
        val e = entrance ?: return false
        if (nowMs - fix.timeMs > staleMs) return false
        val d = Geo.haversineM(fix.lat, fix.lng, e.lat, e.lng)
        lastDistanceM = d
        if (!armed) {
            if (d > rearmM) armed = true
            return false
        }
        if (d < triggerM) {
            armed = false
            return true
        }
        return false
    }

    /** The manual "AR" button counts as this route's firing, so the automatic trigger does not fire again at 40 m. */
    fun consume() { armed = false }
}
