package com.campusmaps.geo

import com.campusmaps.data.Geo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Replays a scripted approach: PAUSED while "localizing" for [localizeSteps] steps, then TRACKING with accuracy
 * tightening from [startAccuracyM] to [endAccuracyM] while walking from [from] to [to] at [speedMps].
 * For JVM tests (call [step] by hand) and emulator QA (call [play] with a scope). One step is one second of walking.
 */
class FakeGeospatialProvider(
    private val from: LatLng,
    private val to: LatLng,
    private val speedMps: Double = 1.3,
    private val localizeSteps: Int = 3,
    private val startAccuracyM: Double = 18.0,
    private val endAccuracyM: Double = 3.0,
    private val vpsAnswer: VpsStatus = VpsStatus.AVAILABLE,
) : GeospatialProvider {
    private val _state = MutableStateFlow(GeoState())
    override val state: StateFlow<GeoState> = _state.asStateFlow()
    private var t = 0
    private var job: Job? = null
    private val totalM = Geo.haversineM(from.lat, from.lng, to.lat, to.lng)

    override fun start() { if (_state.value.tracking == EarthTracking.OFF) _state.value = GeoState(tracking = EarthTracking.PAUSED) }

    override fun stop() { job?.cancel(); job = null; t = 0; _state.value = GeoState() }

    override fun checkVps(lat: Double, lng: Double) { _state.update { it.copy(vps = vpsAnswer) } }

    override fun placeTerrainAnchor(lat: Double, lng: Double): Boolean {
        if (_state.value.tracking != EarthTracking.TRACKING) return false
        _state.update { it.copy(anchor = AnchorStatus.READY) }
        return true
    }

    /** Advance one second of the script. */
    fun step() {
        if (_state.value.tracking == EarthTracking.OFF) return
        t++
        if (t <= localizeSteps) return
        val walked = ((t - localizeSteps) * speedMps).coerceAtMost(totalM)
        val f = if (totalM > 0) walked / totalM else 1.0
        val acc = startAccuracyM + (endAccuracyM - startAccuracyM) * (f * 3).coerceAtMost(1.0)
        val heading = (Math.toDegrees(atan2((to.lng - from.lng) * cos(Math.toRadians(from.lat)), to.lat - from.lat)) + 360) % 360
        _state.update {
            it.copy(
                tracking = EarthTracking.TRACKING,
                lat = from.lat + (to.lat - from.lat) * f,
                lng = from.lng + (to.lng - from.lng) * f,
                headingDeg = heading,
                horizontalAccuracyM = acc,
                yawAccuracyDeg = 8.0,
            )
        }
    }

    /** Current fake position as a location fix (feeds the 40 m trigger in emulator QA). */
    fun asFix(timeMs: Long): LocationFix? = state.value.let { s ->
        if (s.lat == null || s.lng == null) null else LocationFix(s.lat, s.lng, s.horizontalAccuracyM ?: 20.0, timeMs)
    }

    /** Emulator QA: run the script at one step per [periodMs]. */
    fun play(scope: CoroutineScope, periodMs: Long = 1000) {
        start()
        job?.cancel()
        job = scope.launch { while (true) { delay(periodMs); step() } }
    }
}
