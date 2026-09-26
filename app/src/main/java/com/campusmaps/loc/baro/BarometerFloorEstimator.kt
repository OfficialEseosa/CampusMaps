package com.campusmaps.loc.baro

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Floor from air pressure (docs/03 section 3). Pure logic, no Android; JVM-tested in BarometerFloorEstimatorTest.
 *
 * Absolute pressure says nothing about the floor (weather, device offset), so the estimator only measures change from a
 * reference taken on a known floor:
 *
 *     estimate = refFloor + (refPressure - filtered) / hPaPerFloor      (pressure falls going up)
 *
 * - [calibrate]: reference = median of the raw readings of the next [calibrationMs] (route start, at the start node).
 * - [rezero]: reference = the filtered pressure right now (sign fix, floor tap, a hallway step done on a known floor).
 * - The committed [floor] changes only while a ride is on ([rideStarted] .. [rideEnded] + [afterRideMs]), when the
 *   rounded estimate has stayed the same for [stableMs] and is more than [minFloorFraction] of a floor away.
 *   Outside a ride the same change is logged as drift and ignored (doors and air handling move pressure 0.1 to 0.3 hPa).
 */
class BarometerFloorEstimator(
    floorHeightM: Double,
    val hPaPerMetre: Double = HPA_PER_METRE,
    val filterTauMs: Double = 1_000.0,
    val stableMs: Long = 1_500,
    val minFloorFraction: Double = 0.6,
    val calibrationMs: Long = 2_000,
    val afterRideMs: Long = 20_000,
    private val log: (String) -> Unit = {},
) {
    val hPaPerFloor: Double = hPaPerMetre * floorHeightM

    /** Low-passed pressure (hPa), null before the first reading. */
    var filtered: Double? = null
        private set
    var lastRaw: Double? = null
        private set
    var refPressure: Double? = null
        private set
    var refFloor: Int = 0
        private set
    /** The floor the barometer stands behind now. */
    var floor: Int = 0
        private set

    private var lastMs: Long? = null
    private var calibratingUntil: Long? = null
    private val calibrationSamples = ArrayList<Double>()
    private var inRide = false
    private var rideEndMs = Long.MIN_VALUE / 2
    private var candidate: Int? = null
    private var candidateSince = 0L
    private var driftLogged: Int? = null

    val calibrating: Boolean get() = calibratingUntil != null

    /** Unrounded floor estimate, null until calibrated. */
    val estimate: Double?
        get() {
            val r = refPressure ?: return null
            val f = filtered ?: return null
            return refFloor + (r - f) / hPaPerFloor
        }

    /** Pressure change from the reference in hPa (negative = went up). */
    val delta: Double? get() = refPressure?.let { r -> filtered?.let { it - r } }

    /** Start of a route: stand on [knownFloor]; the reference is the median of the next [calibrationMs] of readings. */
    fun calibrate(knownFloor: Int, nowMs: Long) {
        refFloor = knownFloor
        floor = knownFloor
        refPressure = null
        calibrationSamples.clear()
        calibratingUntil = nowMs + calibrationMs
        candidate = null
        log("calibrating at floor $knownFloor for ${calibrationMs} ms")
    }

    /** A hard fix put the walker on [knownFloor]: re-zero on the current filtered pressure. */
    fun rezero(knownFloor: Int, why: String) {
        val f = filtered
        if (f == null || calibrating) {
            // Nothing to re-zero on yet (or the start calibration is still running): keep the floor, take the reference later.
            refFloor = knownFloor
            floor = knownFloor
            return
        }
        refPressure = f
        refFloor = knownFloor
        floor = knownFloor
        candidate = null
        driftLogged = null
        log("re-zero ($why): floor $knownFloor @ ${"%.2f".format(f)} hPa")
    }

    fun rideStarted(nowMs: Long) {
        if (!inRide) log("ride on")
        inRide = true
    }

    fun rideEnded(nowMs: Long) {
        if (inRide) log("ride over; floor changes allowed for ${afterRideMs / 1000} s more")
        if (inRide) rideEndMs = nowMs
        inRide = false
    }

    /** True while floor changes are acted on. */
    fun gateOpen(nowMs: Long): Boolean = inRide || nowMs - rideEndMs <= afterRideMs

    /** Feeds one reading. Returns the new floor when the committed floor changes, else null. */
    fun onPressure(hPa: Double, nowMs: Long): Int? {
        lastRaw = hPa
        val prevMs = lastMs
        val f0 = filtered
        filtered = if (f0 == null || prevMs == null) hPa else {
            val dt = (nowMs - prevMs).coerceAtLeast(0).toDouble()
            val alpha = 1.0 - exp(-dt / filterTauMs)
            f0 + alpha * (hPa - f0)
        }
        lastMs = nowMs

        calibratingUntil?.let { until ->
            calibrationSamples += hPa
            if (nowMs >= until && calibrationSamples.isNotEmpty()) {
                val median = calibrationSamples.sorted().let { s ->
                    if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
                }
                refPressure = median
                filtered = median
                calibratingUntil = null
                log("calibrated: floor $refFloor @ ${"%.2f".format(median)} hPa (${calibrationSamples.size} readings)")
            }
            return null
        }

        val est = estimate ?: return null
        val rounded = est.roundToInt()
        val far = abs(est - floor) > minFloorFraction
        if (rounded == floor || !far) {
            candidate = null
            return null
        }
        if (candidate != rounded) {
            candidate = rounded
            candidateSince = nowMs
            return null
        }
        if (nowMs - candidateSince < stableMs) return null

        if (!gateOpen(nowMs)) {
            if (driftLogged != rounded) {
                driftLogged = rounded
                log("drift ignored (not riding): estimate ${"%.2f".format(est)} vs floor $floor, delta ${"%.2f".format(delta ?: 0.0)} hPa")
            }
            return null
        }
        val from = floor
        floor = rounded
        candidate = null
        driftLogged = null
        log("floor $from -> $rounded (estimate ${"%.2f".format(est)}, delta ${"%.2f".format(delta ?: 0.0)} hPa)")
        return rounded
    }

    companion object {
        /** Pressure falls about 0.12 hPa per metre near sea level. CS and KL measured 0.113 and 0.112. */
        const val HPA_PER_METRE = 0.12
    }
}
