package com.campusmaps.survey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.OffsetDateTime

/**
 * Reader for the CampusSurvey raw observation log (format "campussurvey-log", v2, v1 tolerated).
 * Written from the format documentation in survey-app/README.md only. Every field is optional except kind and id.
 */
@Serializable
data class SurveyLog(val format: String? = null, val formatVersion: Int = 1, val session: SurveySession, val observations: List<Observation> = emptyList()) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        fun parse(text: String): SurveyLog = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class SurveySession(
    val id: String? = null, val building: String, val surveyor: String? = null, val startedAt: String? = null,
    val strideM: Double? = null, val strideCalibrated: Boolean = false, val device: String? = null,
    /** CampusSurvey 0.3: how the stride was calibrated (`gps-walk`, `known-distance`, `typed`). */
    val strideMethod: String? = null, val appVersion: String? = null,
)

@Serializable
data class Gps(val lat: Double, val lng: Double, val accuracyM: Double? = null, val samples: Int? = null)

@Serializable
data class Snapshot(val at: String? = null, val pressureHpa: Double? = null, val forwardHeadingDeg: Double? = null, val lightLux: Double? = null, val gps: Gps? = null)

@Serializable
data class Ocr(val read: String? = null, val maxCharHeightPx: Int? = null, val charHeightAt896: Int? = null)

@Serializable
data class Observation(
    val kind: String,
    val id: Int,
    val floor: Int = 1,
    val snapshot: Snapshot? = null,
    val note: String? = null,
    // node
    val nodeType: String? = null, val name: String? = null, val outdoor: Boolean? = null,
    val headingMeanDeg: Double? = null, val headingSpreadDeg: Double? = null, val doorSide: String? = null,
    // anchor
    val anchorId: String? = null, val photos: List<String> = emptyList(), val widthCm: Double? = null, val text: String? = null,
    val ocr: Ocr? = null, val ocrFar: Ocr? = null, val nearestNodeId: Int? = null, val heightCm: Double? = null,
    val offsetFromNodeM: Double? = null, val facingDeg: Double? = null,
    /** CampusSurvey 0.3: true when the width was estimated (phone or A4 sheet) rather than taped. */
    val widthEstimated: Boolean? = null,
    // edge
    val fromNodeId: Int? = null, val toNodeId: Int? = null, val steps: Int? = null, val durationSec: Double? = null,
    val strideM: Double? = null, val distanceM: Double? = null,
    // elevator, stairs. CampusSurvey 0.3 (session CS-20260925-1238) writes fromFloor, toFloor, waitSec, rideSec (elevator),
    // durationSec and steps (stairs), plus a pressureTrace; the observation's own `floor` is the floor selector AFTER the ride,
    // i.e. the arrival floor. Older names (arrivalFloor, Called/Boarded/Doors-opened timestamps) are still read leniently:
    // any may be missing, a number may arrive as a string, and junk is ignored instead of failing the whole log.
    @SerialName("fromFloor") val fromFloorRaw: JsonElement? = null, @SerialName("toFloor") val toFloorRaw: JsonElement? = null,
    @SerialName("arrivalFloor") val arrivalFloorRaw: JsonElement? = null,
    @SerialName("waitSec") val waitSecRaw: JsonElement? = null, @SerialName("rideSec") val rideSecRaw: JsonElement? = null,
    val calledAt: JsonElement? = null, val boardedAt: JsonElement? = null, val doorsOpenedAt: JsonElement? = null,
    // video (walkthrough): file, sensor log, label; durationSec above
    val file: String? = null, val sensorLogFile: String? = null, val label: String? = null,
    // v1 names
    val gps: Gps? = null, val headingDeg: Double? = null, val fromObservation: Int? = null, val toObservation: Int? = null,
    val nearestObservation: Int? = null,
) {
    val position: Gps? get() = snapshot?.gps ?: gps
    val from: Int? get() = fromNodeId ?: fromObservation
    val to: Int? get() = toNodeId ?: toObservation
    val nearest: Int? get() = nearestNodeId ?: nearestObservation
    val type: String get() = nodeType.orEmpty().uppercase()

    /**
     * Departure floor: `fromFloor`, else the observation's own floor, but only when that differs from the arrival floor
     * (0.3 logs switch the floor selector before saving, so there `floor` is the arrival floor and says nothing about the start).
     */
    val fromFloor: Int? get() = fromFloorRaw.num()?.toInt() ?: floor.takeIf { (kind == "elevator" || kind == "stairs") && it != toFloor }
    /** Arrival floor: `toFloor`, else `arrivalFloor` (README: "enter the arrival floor"). */
    val toFloor: Int? get() = (toFloorRaw.num() ?: arrivalFloorRaw.num())?.toInt()
    /** `waitSec`, else boardedAt - calledAt. */
    val waitSec: Double? get() = waitSecRaw.num() ?: seconds(calledAt, boardedAt)
    /** `rideSec`, else doorsOpenedAt - boardedAt. */
    val rideSec: Double? get() = rideSecRaw.num() ?: seconds(boardedAt, doorsOpenedAt)

    private companion object {
        fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.trim().toDoubleOrNull() }
        /** Epoch milliseconds (number) or ISO-8601 with offset (string). */
        fun JsonElement?.epochMs(): Double? = num() ?: (this as? JsonPrimitive)?.content?.let {
            runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli().toDouble() }.getOrNull() }
        fun seconds(a: JsonElement?, b: JsonElement?): Double? {
            val x = a.epochMs() ?: return null; val y = b.epochMs() ?: return null
            return ((y - x) / 1000).takeIf { it >= 0 }
        }
    }
}
