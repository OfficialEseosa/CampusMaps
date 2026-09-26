package com.campusmaps.loc.baro

/**
 * One guidance session's barometer: feeds [PressureSource] readings to a [BarometerFloorEstimator] and calls [onFloor]
 * when the floor changes during a ride. Logcat tag "Baro".
 */
class BarometerFloorTracker(
    private val source: PressureSource,
    floorHeightM: Double,
    private val now: () -> Long,
    private val onFloor: (Int) -> Unit,
    private val publish: (BaroStatus) -> Unit = {},
) {
    val estimator = BarometerFloorEstimator(floorHeightM, log = ::log)
    private var running = false
    private var lastPublishMs = Long.MIN_VALUE / 2
    /** Filtered pressure when the current ride started, to tell a moving car from a still one. */
    private var rideStartHpa: Double? = null

    val floor: Int get() = estimator.floor

    /** Route start at [startFloor]: calibrate over the first 2 s. Returns false without a barometer. */
    fun start(startFloor: Int): Boolean {
        estimator.calibrate(startFloor, now())
        running = source.start { hPa, t -> onReading(hPa, t) }
        log(if (running) "started, floor height ${"%.2f".format(estimator.hPaPerFloor)} hPa per floor" else "no barometer")
        return running
    }

    fun stop() {
        if (running) source.stop()
        running = false
        BaroFeed.clear()
        log("stopped")
    }

    fun rezero(knownFloor: Int, why: String) = estimator.rezero(knownFloor, why)

    fun rideStarted() {
        if (rideStartHpa == null) rideStartHpa = estimator.filtered
        estimator.rideStarted(now())
    }

    fun rideEnded() {
        rideStartHpa = null
        estimator.rideEnded(now())
    }

    /** True while the gate is open (ride, or the 20 s after it). */
    fun gateOpen(): Boolean = estimator.gateOpen(now())

    /** How far the filtered pressure has moved since the ride started (hPa, absolute). */
    fun movedSinceRideStart(): Double {
        val a = rideStartHpa ?: return 0.0
        val f = estimator.filtered ?: return 0.0
        return kotlin.math.abs(f - a)
    }

    /** True once calibration finished and readings are arriving. */
    val ready: Boolean get() = running && !estimator.calibrating && estimator.refPressure != null

    private fun onReading(hPa: Double, t: Long) {
        val changed = estimator.onPressure(hPa, t)
        if (changed != null) onFloor(changed)
        if (t - lastPublishMs >= 250 || changed != null) {
            lastPublishMs = t
            publish(BaroStatus(estimator.lastRaw, estimator.refFloor, estimator.refPressure, estimator.floor,
                estimator.delta, estimator.calibrating, estimator.gateOpen(t)))
        }
    }

    private fun log(msg: String) {
        runCatching { android.util.Log.i(TAG, msg) }
    }

    companion object { const val TAG = "Baro" }
}
