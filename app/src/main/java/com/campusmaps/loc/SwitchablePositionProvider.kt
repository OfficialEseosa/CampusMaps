package com.campusmaps.loc

import android.util.Log
import com.campusmaps.guidance.Pose
import com.campusmaps.guidance.PositionProvider
import com.campusmaps.guidance.SimulatedPositionProvider
import com.campusmaps.guidance.SimulationControls
import com.campusmaps.route.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

enum class PositionMode {
    /** Camera (ArPositionProvider) whenever the AR view runs and the route is placed; the simulator otherwise. */
    AUTO,
    /** The teammate's simulated student only (debug Step / Walk / jump / continuous walker). */
    SIMULATED,
}

/**
 * What GuidanceController uses as its position source. In [PositionMode.AUTO] the pose is the AR camera pose when the AR
 * view is live and a building-to-world transform exists (sign fix or "Place route here"), else the simulator's.
 *
 * Debug fallbacks keep working through [SimulationControls]: any debug move (Step, Walk, jump, skip, off route, lost,
 * continuous walker on/off) switches to [PositionMode.SIMULATED], starting from where the camera put the student. A new
 * placement or sign fix (transform moved by more than 0.3 m or 3 degrees) switches back to AUTO. [setMode] is there for a
 * future "Position: AR / simulated" debug switch.
 *
 * While AR is expected (ARCore supported, not glasses mode) the continuous walker starts paused, so nobody walks by
 * themselves; if no AR frame arrives within 4 s (camera denied, AR forced off) it resumes as before.
 */
class SwitchablePositionProvider(
    private val scope: CoroutineScope,
    private val sim: SimulatedPositionProvider,
    private val ar: ArPositionProvider,
    private val transform: StateFlow<BuildingToWorld?>,
) : PositionProvider, SimulationControls {

    private val _mode = MutableStateFlow(PositionMode.AUTO)
    val mode: StateFlow<PositionMode> = _mode.asStateFlow()

    override val pose: StateFlow<Pose> = combine(sim.pose, ar.pose, ar.live, transform, _mode) { s, a, live, t, m ->
        if (m == PositionMode.AUTO && live && t != null && ar.hasFix) a else s
    }.stateIn(scope, SharingStarted.Eagerly, sim.pose.value)

    /** True when the pose shown now comes from the camera. */
    val usingAr: Boolean get() = _mode.value == PositionMode.AUTO && ar.live.value && transform.value != null && ar.hasFix

    private var lastT: BuildingToWorld? = null

    init {
        scope.launch {
            ar.live.collect { live ->
                if (_mode.value != PositionMode.AUTO) return@collect
                if (live) {
                    sim.paused = true
                    Log.i(TAG, "AR view live: camera drives the position once the route is placed")
                } else if (ar.hasFix) {
                    // AR view left or failed: carry on from the camera's last position with the walker.
                    seedSim()
                    sim.paused = false
                    Log.i(TAG, "AR view gone: simulated walker resumes")
                }
            }
        }
        scope.launch {
            transform.collect { t ->
                val prev = lastT
                lastT = t
                if (t != null && _mode.value == PositionMode.SIMULATED && (prev == null || bigChange(prev, t))) {
                    setMode(PositionMode.AUTO)
                }
            }
        }
    }

    /** Called once by GuidanceController.start(). */
    fun start(arExpected: Boolean) {
        if (!arExpected) return
        sim.paused = true
        scope.launch {
            delay(4_000)
            if (!ar.live.value && _mode.value == PositionMode.AUTO) {
                sim.paused = false
                Log.i(TAG, "no AR frames after 4 s: simulated walker resumes")
            }
        }
    }

    fun setMode(m: PositionMode) {
        if (m == _mode.value) return
        if (m == PositionMode.SIMULATED && usingAr) seedSim()
        if (m == PositionMode.AUTO && ar.live.value) sim.paused = true
        _mode.value = m
        Log.i(TAG, "position mode $m")
    }

    private fun toSim(seed: Boolean = true) {
        if (_mode.value == PositionMode.SIMULATED) return
        if (seed && usingAr) seedSim()
        _mode.value = PositionMode.SIMULATED
        Log.i(TAG, "position mode SIMULATED (debug control)")
    }

    private fun seedSim() {
        val a = ar.pose.value
        sim.placeAt(a.position, a.floor)
    }

    private fun bigChange(a: BuildingToWorld, b: BuildingToWorld) =
        hypot(hypot(a.tx - b.tx, a.ty - b.ty), a.tz - b.tz) > 0.3 || abs(Math.toDegrees(BuildingToWorld.wrapPi(a.yawRad - b.yawRad))) > 3.0

    override fun follow(route: Route) {
        sim.follow(route)
        ar.follow(route)
    }

    override fun stop() {
        sim.stop()
    }

    // ---- SimulationControls: every debug move falls back to the simulator ----
    override fun dropConfidence() { toSim(); sim.dropConfidence() }
    override fun pushOffRoute() { toSim(); sim.pushOffRoute() }
    override fun jumpToPoint(index: Int) { toSim(seed = false); sim.jumpToPoint(index) }
    override fun placeAt(position: com.campusmaps.data.model.Point, floor: Int) { toSim(seed = false); sim.placeAt(position, floor) }
    override var speedMultiplier: Double
        get() = sim.speedMultiplier
        set(v) { sim.speedMultiplier = v }
    // Reads "not paused" while the camera drives, so the debug link's first tap pauses (student stands still, Step works).
    override var paused: Boolean
        get() = if (usingAr) false else sim.paused
        set(v) { toSim(); sim.paused = v }
    override var lowConfidence: Boolean
        get() = sim.lowConfidence
        set(v) { sim.lowConfidence = v }

    private companion object { const val TAG = "Position" }
}
