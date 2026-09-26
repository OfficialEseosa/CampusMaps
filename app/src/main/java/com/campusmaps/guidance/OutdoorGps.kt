package com.campusmaps.guidance

import com.campusmaps.geo.LocationFix
import com.campusmaps.outdoor.LatLngPoint
import com.campusmaps.outdoor.OutdoorRoutes
import com.campusmaps.route.Route
import com.campusmaps.route.RouteStep
import kotlin.math.abs

// Outdoor steps of an Explore start (RouteStep.outdoorEnd set) are followed by FusedLocation, not by the fake walker.
// Pure Kotlin so the rules are unit tested (OutdoorGpsTest).
object OutdoorGps {
    // The current outdoor step is done this close (GPS metres) to its end point. Tunable.
    const val COMPLETE_M = 12.0
    // A fix older than this is stale: the walker (or the debug Step) drives the outdoor steps again.
    const val FRESH_MS = 10_000L
    // The "Enter" step usually becomes current at the same moment the last street step is done (both end at the door),
    // so it stays up at least this long before the indoor steps take over.
    const val ENTER_MIN_SHOW_MS = 3_000L

    fun isOutdoor(step: RouteStep?): Boolean = step?.outdoorEnd != null

    fun fresh(fix: LocationFix?, nowMs: Long): Boolean = fix != null && abs(nowMs - fix.timeMs) <= FRESH_MS

    /** GPS metres from [fix] to [step]'s end point; null when the step is not an outdoor step or there is no fix. */
    fun distanceM(step: RouteStep, fix: LocationFix?): Double? {
        val end = step.outdoorEnd ?: return null
        val f = fix ?: return null
        return OutdoorRoutes.distanceM(LatLngPoint(f.lat, f.lng), end)
    }

    /** True when a fresh fix is within [COMPLETE_M] of the current outdoor step's end (and the "Enter" step was read). */
    fun completes(route: Route, stepIndex: Int, fix: LocationFix?, nowMs: Long, shownAtMs: Long?): Boolean {
        val step = route.steps.getOrNull(stepIndex) ?: return false
        if (!isOutdoor(step) || !fresh(fix, nowMs)) return false
        val d = distanceM(step, fix) ?: return false
        if (d >= COMPLETE_M) return false
        val lastOutdoor = !isOutdoor(route.steps.getOrNull(stepIndex + 1))
        val minShow = if (lastOutdoor && stepIndex > 0 && isOutdoor(route.steps[stepIndex - 1])) ENTER_MIN_SHOW_MS else 0L
        return shownAtMs == null || nowMs - shownAtMs >= minShow
    }

    /**
     * The step index after one tick. [engine] is what GuidanceEngine decided from the (fake or AR) pose.
     * While the current step is outdoor and the fix is fresh, the walker cannot move it: GPS completes it
     * (one step per tick), or [force] (debug Step / Skip) does. With a stale fix the engine's answer stands,
     * except that [force] still moves exactly one step.
     */
    fun nextIndex(route: Route, previous: Int, engine: Int, fix: LocationFix?, nowMs: Long, shownAtMs: Long?, force: Boolean): Int {
        if (!isOutdoor(route.steps.getOrNull(previous))) return engine
        val last = route.steps.lastIndex
        if (force) return (previous + 1).coerceAtMost(last)
        if (!fresh(fix, nowMs)) return engine
        return if (completes(route, previous, fix, nowMs, shownAtMs)) (previous + 1).coerceAtMost(last) else previous
    }

    /** Banner and watch distance: GPS to the step's end with a fresh fix; along the route otherwise. */
    fun displayDistanceM(step: RouteStep, fix: LocationFix?, nowMs: Long, alongM: Double, engineDistanceM: Double): Double {
        if (!isOutdoor(step)) return engineDistanceM
        if (fresh(fix, nowMs)) distanceM(step, fix)?.let { return it }
        return (step.completeAtM - alongM).coerceAtLeast(0.0)
    }
}
