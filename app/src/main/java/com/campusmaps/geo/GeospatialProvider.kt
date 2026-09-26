package com.campusmaps.geo

import kotlinx.coroutines.flow.StateFlow

/** Earth tracking, as the UI needs it. Mirrors ARCore's TrackingState plus the reasons it never starts. */
enum class EarthTracking {
    /** start() not called, or the provider was stopped. */
    OFF,
    /** Starting, or ARCore is localizing (TrackingState.PAUSED). */
    PAUSED,
    TRACKING,
    /** Cannot work on this run: no ARCore, no API key, geospatial unsupported, no location permission. See [GeoState.failure]. */
    UNAVAILABLE,
}

/** Result of checkVpsAvailabilityAsync at the start point (docs/03 section 4). */
enum class VpsStatus { UNKNOWN, CHECKING, AVAILABLE, UNAVAILABLE, ERROR }

/** The entrance Terrain anchor. */
enum class AnchorStatus { NONE, RESOLVING, READY, FAILED }

/** One snapshot of the outdoor Geospatial state. Pure data, no ARCore types, so it is JVM-testable. */
data class GeoState(
    val tracking: EarthTracking = EarthTracking.OFF,
    val lat: Double? = null,
    val lng: Double? = null,
    val headingDeg: Double? = null,
    /** Metres (ARCore's horizontal accuracy). */
    val horizontalAccuracyM: Double? = null,
    /** Degrees (ARCore's orientation yaw accuracy). */
    val yawAccuracyDeg: Double? = null,
    val vps: VpsStatus = VpsStatus.UNKNOWN,
    val anchor: AnchorStatus = AnchorStatus.NONE,
    /** Why the provider cannot run, or the last thing that went wrong, in plain words for Logcat and the debug card. */
    val failure: String? = null,
)

/**
 * The outdoor leg's Geospatial seam (docs/03 section 4). The ARCore implementation is fed by the AR view's session;
 * the fake replays a scripted approach for tests and the emulator.
 */
interface GeospatialProvider {
    val state: StateFlow<GeoState>

    /** Begin (or resume) Earth tracking. Safe to call more than once. */
    fun start()

    /** Stop tracking and drop the entrance anchor. */
    fun stop()

    /** Ask for VPS coverage at (lat, lng); the answer lands in [GeoState.vps]. */
    fun checkVps(lat: Double, lng: Double)

    /** Place (or replace) the entrance Terrain anchor. Returns false if it cannot be attempted now (not tracking, no session). */
    fun placeTerrainAnchor(lat: Double, lng: Double): Boolean
}

/** docs/03 section 4 and docs/research/arcore.md section 3: the Geospatial samples' gate before drawing anything. */
object OutdoorArrowGate {
    const val MAX_HORIZONTAL_ACCURACY_M = 10.0
    const val MAX_YAW_ACCURACY_DEG = 15.0

    /** Arrows only while Earth is TRACKING with horizontal accuracy under 10 m (and yaw under 15 degrees when known). */
    fun shouldDrawArrows(s: GeoState): Boolean {
        if (s.tracking != EarthTracking.TRACKING) return false
        val h = s.horizontalAccuracyM ?: return false
        if (h >= MAX_HORIZONTAL_ACCURACY_M) return false
        val yaw = s.yawAccuracyDeg
        return yaw == null || yaw < MAX_YAW_ACCURACY_DEG
    }

    /** Text of the chip at the top of S2 on the outdoor leg (board 04). */
    fun chipText(s: GeoState): String = if (shouldDrawArrows(s)) "AR tracking on" else "Finding your position"
}
