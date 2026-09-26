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

    suspend fun walking(from: LatLngPoint, to: LatLngPoint): OutdoorRoute? {
        if (!enabled) return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                try {
                    val url = URL(
                        "https://maps.googleapis.com/maps/api/directions/json?mode=walking" +
                            "&origin=${from.lat},${from.lng}&destination=${to.lat},${to.lng}&key=$apiKey"
                    )
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = TIMEOUT_MS.toInt()
                        readTimeout = TIMEOUT_MS.toInt()
                    }
                    val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
                    parse(body)
                } catch (e: Exception) {
                    Log.w(TAG, "Directions failed, using a straight line: ${e.message}")
                    null
                }
            }
        }
    }

    companion object {
        private const val TAG = "Directions"
        const val TIMEOUT_MS = 5_000L

        // Pure parser (tested on the JVM). Null unless status is OK with at least one route.
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
