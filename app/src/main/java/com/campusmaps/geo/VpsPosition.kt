package com.campusmaps.geo

import com.campusmaps.data.Geo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import com.campusmaps.data.Building as CoreBuilding

/**
 * The outdoor leg's best position: Geospatial (VPS, about 1 m) while Earth is TRACKING under 10 m, else FusedLocation.
 * S2's OutdoorGeoEffect publishes the Geospatial position here; MainViewModel feeds [preferVps] to the banner distance and
 * the 40 m hand-off trigger, and asks [snap] whether the student is standing at an entrance.
 */
object VpsPosition {
    const val MAX_ACCURACY_M = 10.0
    const val SNAP_M = 15.0
    /** A VPS fix newer than this hides FusedLocation fixes. */
    const val FRESH_MS = 3_000L

    /** The latest good Geospatial position (null when not tracking well). Written by S2, read by MainViewModel. */
    val fixes = MutableStateFlow<LocationFix?>(null)

    /** [s] as a fix when Earth is TRACKING with horizontal accuracy under 10 m; null otherwise. */
    fun fixOf(s: GeoState, nowMs: Long): LocationFix? {
        if (s.tracking != EarthTracking.TRACKING) return null
        val h = s.horizontalAccuracyM ?: return null
        if (h >= MAX_ACCURACY_M) return null
        val lat = s.lat ?: return null
        val lng = s.lng ?: return null
        return LocationFix(lat, lng, h, nowMs)
    }

    /** FusedLocation fixes, except while a fresh VPS fix exists; plus every VPS fix. */
    fun preferVps(fused: Flow<LocationFix>, vps: MutableStateFlow<LocationFix?> = fixes, nowMs: () -> Long = System::currentTimeMillis): Flow<LocationFix> =
        merge(
            fused.filter { f -> vps.value.let { v -> v == null || nowMs() - v.timeMs > FRESH_MS } },
            vps.filterNotNull(),
        )

    /** Where the VPS position says the student stands: [entranceId], metres away, and whether it differs from the planned one. */
    data class Snap(val entranceId: String, val distanceM: Double, val reroute: Boolean)

    /** Nearest outdoor entrance of [core] within [SNAP_M] of [fix] (any entrance, not only [plannedEntranceId]); null if none. */
    fun snap(core: CoreBuilding, fix: LocationFix, plannedEntranceId: String?): Snap? {
        val best = core.nodes.filter { it.isOutdoorEntrance && it.lat != null && it.lng != null }
            .map { it to Geo.haversineM(fix.lat, fix.lng, it.lat!!, it.lng!!) }
            .minByOrNull { it.second } ?: return null
        if (best.second > SNAP_M) return null
        return Snap(best.first.id, best.second, best.first.id != plannedEntranceId)
    }

    /** The Logcat line (tag Geo): "VPS snap to E-WM, 6.2 m". */
    fun logLine(s: Snap): String = "VPS snap to ${s.entranceId}, %.1f m".format(s.distanceM) + if (s.reroute) " (planned another door: reroute)" else ""
}
