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
