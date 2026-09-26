package com.campusmaps.outdoor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

// Google Directions API, walking mode. Only called when a key is set and the phone is online; any error, a non-OK
// status or more than 5 s returns null and the caller falls back to the straight line.
class DirectionsClient(private val apiKey: String) {

    val enabled: Boolean get() = apiKey.isNotBlank()

    // Routes API (computeRoutes, WALK) first; the legacy Directions API second (new Cloud projects cannot enable it).
    suspend fun walking(from: LatLngPoint, to: LatLngPoint): OutdoorRoute? {
        if (!enabled) return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                try {
                    routesApi(from, to) ?: legacy(from, to)
                } catch (e: Exception) {
                    Log.w(TAG, "Directions failed, using a straight line: ${e.message}")
                    null
                }
            }
        }
    }

    private fun routesApi(from: LatLngPoint, to: LatLngPoint): OutdoorRoute? {
        val conn = (URL("https://routes.googleapis.com/directions/v2:computeRoutes").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT_MS.toInt(); readTimeout = TIMEOUT_MS.toInt()
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey)
            setRequestProperty("X-Goog-FieldMask",
                "routes.distanceMeters,routes.polyline.encodedPolyline,routes.legs.steps.navigationInstruction.instructions")
        }
        return try {
            conn.outputStream.use { it.write(routesBody(from, to).toByteArray()) }
            if (conn.responseCode != 200) {
                Log.w(TAG, "Routes API HTTP ${conn.responseCode}; trying legacy Directions")
                null
            } else parseRoutes(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    private fun legacy(from: LatLngPoint, to: LatLngPoint): OutdoorRoute? {
        val conn = (URL(
            "https://maps.googleapis.com/maps/api/directions/json?mode=walking" +
                "&origin=${from.lat},${from.lng}&destination=${to.lat},${to.lng}&key=$apiKey"
        ).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS.toInt(); readTimeout = TIMEOUT_MS.toInt()
        }
        val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
        return parse(body).also { if (it == null) Log.w(TAG, "Legacy Directions gave no route (status not OK); straight line") }
    }

    companion object {
        private const val TAG = "Directions"
        const val TIMEOUT_MS = 5_000L

        fun routesBody(from: LatLngPoint, to: LatLngPoint): String =
            """{"origin":{"location":{"latLng":{"latitude":${from.lat},"longitude":${from.lng}}}},""" +
                """"destination":{"location":{"latLng":{"latitude":${to.lat},"longitude":${to.lng}}}},"travelMode":"WALK"}"""

        // Routes API response parser (tested on the JVM). Null without a route or a polyline.
        fun parseRoutes(body: String): OutdoorRoute? {
            val route = Json.parseToJsonElement(body).jsonObject["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val points = route["polyline"]?.jsonObject?.get("encodedPolyline")?.jsonPrimitive?.content
                ?.let(OutdoorRoutes::decodePolyline).orEmpty()
            if (points.size < 2) return null
            val distance = route["distanceMeters"]?.jsonPrimitive?.int?.toDouble() ?: OutdoorRoutes.distanceM(points.first(), points.last())
            val steps = route["legs"]?.jsonArray?.firstOrNull()?.jsonObject?.get("steps")?.jsonArray.orEmpty().mapNotNull { s ->
                (s.jsonObject["navigationInstruction"] as? JsonObject)?.get("instructions")?.jsonPrimitive?.content
                    ?.replace('\n', ' ')?.trim()?.takeIf { it.isNotBlank() }
            }
            return OutdoorRoute(points, distance, OutdoorRoutes.walkingMinutes(distance), steps, RouteSource.DIRECTIONS)
        }

        // Legacy Directions parser (tested on the JVM). Null unless status is OK with at least one route.
        fun parse(body: String): OutdoorRoute? {
            val root = Json.parseToJsonElement(body).jsonObject
            if (root["status"]?.jsonPrimitive?.content != "OK") return null
            val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val leg = route["legs"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val points = route["overview_polyline"]?.jsonObject?.get("points")?.jsonPrimitive?.content
                ?.let(OutdoorRoutes::decodePolyline).orEmpty()
            if (points.size < 2) return null
            val distance = (leg["distance"] as? JsonObject)?.get("value")?.jsonPrimitive?.int?.toDouble() ?: return null
            val steps = leg["steps"]?.jsonArray.orEmpty().mapNotNull { s ->
                s.jsonObject["html_instructions"]?.jsonPrimitive?.content?.let(::plainText)?.takeIf { it.isNotBlank() }
            }
            return OutdoorRoute(points, distance, OutdoorRoutes.walkingMinutes(distance), steps, RouteSource.DIRECTIONS)
        }

        // "Head <b>west</b> on <b>Gilmer St</b><div>Destination</div>" -> "Head west on Gilmer St. Destination"
        fun plainText(html: String): String = html
            .replace(Regex("<div[^>]*>"), ". ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace(Regex("\\s+"), " ").replace(" .", ".").trim()
    }
}
