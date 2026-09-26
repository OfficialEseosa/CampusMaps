package com.campusmaps.geo

import android.content.Context
import android.util.Log
import com.campusmaps.data.BuildingLoader
import com.campusmaps.data.model.NodeKind
import com.campusmaps.route.Route
import com.campusmaps.data.Building as CoreBuilding

/** The route's outdoor target: the entrance node it walks to, with its latitude and longitude from the core building file. */
data class GeoEntrance(val nodeId: String, val name: String, val latLng: LatLng)

object GeoEntrances {
    private const val TAG = "Geo"
    private val cache = mutableMapOf<String, CoreBuilding?>()

    /**
     * The entrance the route walks to from outside, or null (logged) if the route does not start outside or the core
     * node has no lat/lng yet (KL and CSE until the data agent adds them). Pure; JVM-testable.
     */
    fun find(core: CoreBuilding?, route: Route): Pair<GeoEntrance?, String?> {
        if (!route.startsOutside) return null to "route does not start outside"
        val p = route.points.firstOrNull { it.node.kind == NodeKind.ENTRANCE } ?: return null to "route has no entrance node"
        if (core == null) return null to "no core building ${route.buildingId}"
        val n = core.nodes.firstOrNull { it.id == p.node.id } ?: return null to "entrance ${p.node.id} not in ${core.code}.json"
        val lat = n.lat; val lng = n.lng
        if (lat == null || lng == null) return null to "entrance ${n.id} in ${core.code}.json has no lat/lng"
        return GeoEntrance(n.id, n.name, LatLng(lat, lng)) to null
    }

    /** Android: reads assets/buildings/<code>.json (cached) and logs why when there is no target. */
    fun forRoute(context: Context, route: Route): GeoEntrance? {
        val core = synchronized(cache) {
            cache.getOrPut(route.buildingId) {
                runCatching {
                    context.assets.open("buildings/${route.buildingId}.json").bufferedReader().use { BuildingLoader.fromJson(it.readText()) }
                }.onFailure { Log.w(TAG, "cannot read ${route.buildingId}.json", it) }.getOrNull()
            }
        }
        val (e, why) = find(core, route)
        if (e == null) Log.w(TAG, "outdoor leg: banner and map only ($why)")
        else Log.i(TAG, "outdoor target ${e.nodeId} ${e.name} at ${e.latLng.lat}, ${e.latLng.lng}")
        return e
    }
}
