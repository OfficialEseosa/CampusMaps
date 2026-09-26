package com.campusmaps.ui

import com.campusmaps.data.campus.CoreBridge
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.OutdoorStart

// "Start AR navigation" on Explore routes S2 from the phone's real fix, like the map did (qa-phone N2): the trip's building
// gets a "Your location" OUTDOOR node at the fix (CoreBridge.withGpsStart), which CoreRouter turns into core
// Start.Outside(lat, lng). Kept out of MainViewModel so its Explore hand-off code stays small.
object ExploreStart {
    /** The start node id and the fix to keep in the Selection: "GPS" with a fix, the building's default start without one. */
    fun start(building: Building, lat: Double?, lng: Double?): Pair<String, OutdoorStart?> =
        if (lat != null && lng != null) CoreBridge.GPS_START_ID to OutdoorStart(lat, lng)
        else building.defaultStartId to null

    /** [base] with the "Your location" node while the selection holds a fix; [base] unchanged otherwise. */
    fun building(base: Building, gps: OutdoorStart?): Building =
        gps?.let { CoreBridge.withGpsStart(base, it.lat, it.lng) } ?: base
}

/**
 * S1 opened directly (not from the Explore map): the default "Where are you?" when the phone already knows.
 * A fresh, good fix away from the building gives the same "Your location" start as Explore; at the building (near an entrance
 * or inside its outline), in demo mode, or with no usable fix the building's default start stays (S1 at Klaus, P1 at CS).
 */
object GpsStart {
    const val MAX_AGE_MS = 30_000L      // Older fixes are ignored
    const val MAX_ACCURACY_M = 50.0     // Worse fixes are ignored
    const val AT_BUILDING_M = 25.0      // Within this (or the fix's accuracy, if larger) of an entrance = at the building
    private const val OUTLINE_MARGIN_M = 5.0
    private const val MOVE_M = 5.0            // A new fix closer than this to the shown one does not change the start

    /** The fix to start from, or null to keep the building's default start. */
    fun decide(building: Building, fix: com.campusmaps.geo.LocationFix?, nowMs: Long, demoMode: Boolean): com.campusmaps.geo.LocationFix? {
        if (demoMode || fix == null) return null
        if (nowMs - fix.timeMs > MAX_AGE_MS || fix.accuracyM >= MAX_ACCURACY_M) return null
        return if (atBuilding(building, fix.lat, fix.lng, fix.accuracyM)) null else fix
    }

    /** Near an entrance (25 m, or the accuracy if larger), or inside the box around the building's nodes. No entrance with a position: treated as at the building. */
    fun atBuilding(building: Building, lat: Double, lng: Double, accuracyM: Double): Boolean {
        val core = building.core
        val doors = core.nodes.filter { it.isOutdoorEntrance && it.lat != null && it.lng != null }
        if (doors.isEmpty()) return true
        val radius = maxOf(AT_BUILDING_M, accuracyM)
        if (doors.any { com.campusmaps.data.Geo.haversineM(lat, lng, it.lat!!, it.lng!!) <= radius }) return true
        val (x, y) = com.campusmaps.data.Geo.toBuilding(core.origin, lat, lng)
        val m = OUTLINE_MARGIN_M
        return x >= core.nodes.minOf { it.x } - m && x <= core.nodes.maxOf { it.x } + m &&
            y >= core.nodes.minOf { it.y } - m && y <= core.nodes.maxOf { it.y } + m
    }

    /** The one-line hint under the picker: "From your location (GPS, 8 m)". */
    fun hint(accuracyM: Double): String = "From your location (GPS, ${kotlin.math.round(accuracyM).toInt()} m)"

    /**
     * The next S1 selection for a new fix / building / settings value. [sel] is kept as is when the user picked a start by
     * hand (or it came from Explore); only an empty start or an earlier GPS default is replaced.
     */
    fun apply(sel: com.campusmaps.ui.Selection, building: Building, fix: com.campusmaps.geo.LocationFix?, nowMs: Long, demoMode: Boolean): com.campusmaps.ui.Selection {
        val auto = sel.gpsAutoAccuracyM != null
        if (sel.startId != null && !auto) return sel // manual pick (or Explore hand-off) wins
        val use = decide(building, fix, nowMs, demoMode)
        return if (use == null) {
            if (auto) sel.copy(startId = null, gps = null, gpsAutoAccuracyM = null) else sel
        } else {
            // Same place and accuracy as the GPS start already shown: keep it, so S1 does not re-plan on every fix.
            val same = auto && sel.gps != null && sel.startId == CoreBridge.GPS_START_ID &&
                com.campusmaps.data.Geo.haversineM(sel.gps.lat, sel.gps.lng, use.lat, use.lng) < MOVE_M &&
                kotlin.math.abs(sel.gpsAutoAccuracyM!! - use.accuracyM) < 1.0
            if (same) sel else sel.copy(startId = CoreBridge.GPS_START_ID, gps = OutdoorStart(use.lat, use.lng), gpsAutoAccuracyM = use.accuracyM)
        }
    }
}
