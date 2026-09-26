package com.campusmaps.data

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.*

object Geo {
    const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /** Equirectangular east/north offset in metres of (lat, lng) from (lat0, lng0). Good to centimetres over 1 km. */
    fun enu(lat0: Double, lng0: Double, lat: Double, lng: Double): Pair<Double, Double> {
        val e = Math.toRadians(lng - lng0) * EARTH_RADIUS_M * cos(Math.toRadians(lat0))
        val n = Math.toRadians(lat - lat0) * EARTH_RADIUS_M
        return e to n
    }

    /** Inverse of [enu]. */
    fun offset(lat0: Double, lng0: Double, eastM: Double, northM: Double): Pair<Double, Double> =
        (lat0 + Math.toDegrees(northM / EARTH_RADIUS_M)) to
            (lng0 + Math.toDegrees(eastM / (EARTH_RADIUS_M * cos(Math.toRadians(lat0)))))

    /** (lat, lng) to building (x, y) using the origin and its heading. */
    fun toBuilding(o: Origin, lat: Double, lng: Double): Pair<Double, Double> {
        val (e, n) = enu(o.lat, o.lng, lat, lng)
        val h = Math.toRadians(o.headingDeg)
        return (e * cos(h) - n * sin(h)) to (e * sin(h) + n * cos(h))
    }

    val COMPASS = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")

    /** Nearest of the 8 compass words. */
    fun compassWord(deg: Double): String = COMPASS[(((deg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]

    /** Unit vector (x, y) for a compass word or a number of degrees, in the building frame (north = +y). */
    fun facingVector(facing: String?): Pair<Double, Double>? {
        if (facing == null) return null
        val deg = COMPASS.indexOf(facing.lowercase()).takeIf { it >= 0 }?.let { it * 45.0 } ?: facing.toDoubleOrNull() ?: return null
        val r = Math.toRadians(deg)
        return sin(r) to cos(r)
    }
}

/** Access rule evaluation. Missing or empty access means always public. */
object Access {
    private val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    /** Parse "Mon-Fri", "Sat-Sun", "Sat", "Mon,Wed,Fri", "Fri-Mon", "Daily". */
    fun parseDays(spec: String): Set<DayOfWeek> {
        if (spec.trim().lowercase() in setOf("daily", "all", "every day")) return DayOfWeek.entries.toSet()
        fun idx(s: String) = DAYS.indexOf(s.trim().lowercase().take(3)).also { require(it >= 0) { "Bad day '$s' in '$spec'" } }
        return spec.split(",").flatMap { part ->
            val ends = part.split("-")
            val a = idx(ends[0]); val b = idx(ends.getOrElse(1) { ends[0] })
            (0..((b - a + 7) % 7)).map { DayOfWeek.of((a + it) % 7 + 1) }
        }.toSet()
    }

    /** "HH:mm" to minutes since midnight, "24:00" allowed. */
    fun minutes(t: String): Int = t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }.also { require(it in 0..1440) { "Bad time $t" } }

    /** True when [w] covers [now]. A window whose close is not after its open runs past midnight into the next day. */
    fun covers(w: AccessWindow, now: LocalDateTime): Boolean {
        val days = parseDays(w.days); val t = now.hour * 60 + now.minute
        val open = minutes(w.open); val close = minutes(w.close)
        return if (open < close) now.dayOfWeek in days && t >= open && t < close
        else (now.dayOfWeek in days && t >= open) || (now.dayOfWeek.minus(1) in days && t < close)
    }

    /** The rule in force: public if any public window covers now, otherwise card (an entrance with windows but none open is card-only). */
    fun ruleAt(node: Node, now: LocalDateTime): AccessRule {
        val windows = node.access ?: return AccessRule.PUBLIC
        if (windows.isEmpty()) return AccessRule.PUBLIC
        return if (windows.any { it.rule == AccessRule.PUBLIC && covers(it, now) }) AccessRule.PUBLIC else AccessRule.CARD
    }

    fun isLocked(node: Node, now: LocalDateTime) = node.type == NodeType.ENTRANCE && ruleAt(node, now) == AccessRule.CARD
}
