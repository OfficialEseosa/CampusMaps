package com.campusmaps.loc

import com.campusmaps.data.model.Point
import com.campusmaps.guidance.Pose
import com.campusmaps.guidance.PositionProvider
import com.campusmaps.route.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.roundToInt

/** ARCore's camera tracking state, without the ARCore type (keeps this file JVM-testable). */
enum class ArTracking { TRACKING, PAUSED, STOPPED }

/**
 * One ARCore frame, reduced to what positioning needs: camera position in world metres and the horizontal part of the
 * direction the phone faces (world X and Z).
 */
data class CameraSample(
    val tracking: ArTracking,
    val x: Double, val y: Double, val z: Double,
    val fwdX: Double, val fwdZ: Double,
    val timeMs: Long,
)

/** Camera pose -> the teammate's floor-plan pose. Pure math, JVM-tested (ArPositionProviderTest). */
object PoseMath {
    /** Assumed height of the phone above the floor when it is held up. Used only to guess the floor from ARCore height. */
    const val PHONE_HEIGHT_M = 1.3

    data class AppPose(val position: Point, val floor: Int, val headingRad: Double?)

    /**
     * Building frame is y-up (core, north = +y); the app's floor plan is y-down (CoreBridge flips y). So the app position is
     * (x, -y) and the app heading (0 = east, PI/2 = south) is minus the building heading. The floor is the transform's
     * reference floor (the anchor's or the tapped node's) plus whole floors of camera height change, so a stair climb that
     * ARCore tracks moves the floor too. Heading is null when the phone points straight up or down.
     */
    fun toAppPose(t: BuildingToWorld, s: CameraSample): AppPose {
        val (bx, by) = t.toBuilding(Vec3(s.x, s.y, s.z))
        val floorsUp = if (t.floorHeightM > 0.1) ((s.y - t.ty - PHONE_HEIGHT_M) / t.floorHeightM).roundToInt() else 0
        val heading = if (hypot(s.fwdX, s.fwdZ) < 1e-3) null else -t.buildingHeadingOf(s.fwdX, s.fwdZ)
        return AppPose(Point(bx, -by), t.refFloor + floorsUp, heading)
    }
}

/**
 * Real position source (docs/03 section 1): the ARCore camera pose, mapped through the building-to-world transform that a
 * sign fix (Augmented Images) or the debug "Place route here" floor tap set. The student moves only when the phone moves.
 *
 * Fed by ArGuidanceView through [ArFeed] every frame; publishes at most every [periodMs] (about 5 Hz). Confidence is 1.0
 * while ARCore tracks, falls by [DECAY_PER_S] per second while tracking is PAUSED (0.5 = "Locate me" after 2 s), and is 0
 * when STOPPED. Without a transform it publishes nothing ([SwitchablePositionProvider] then shows the simulator's pose).
 */
class ArPositionProvider(
    private val transform: () -> BuildingToWorld?,
    initial: Pose,
    private val periodMs: Long = 200,
) : PositionProvider {
    private val _pose = MutableStateFlow(initial)
    override val pose: StateFlow<Pose> = _pose.asStateFlow()

    private val _live = MutableStateFlow(false)
    /** True while an AR view is sending frames. */
    val live: StateFlow<Boolean> = _live.asStateFlow()

    /** True once a pose was computed from a transform (the simulator can then be seeded from it). */
    var hasFix: Boolean = false
        private set

    private var lastEmitMs = Long.MIN_VALUE / 2
    private var lastTrackingMs = Long.MIN_VALUE / 2
    private var lastState: ArTracking? = null

    fun onSample(s: CameraSample) {
        if (!_live.value) _live.value = true
        val t = transform() ?: return
        if (s.tracking == lastState && s.timeMs - lastEmitMs < periodMs) return
        lastState = s.tracking
        lastEmitMs = s.timeMs
        val p = _pose.value
        _pose.value = when (s.tracking) {
            ArTracking.TRACKING -> {
                lastTrackingMs = s.timeMs
                hasFix = true
                val a = PoseMath.toAppPose(t, s)
                p.copy(position = a.position, floor = a.floor, headingRad = a.headingRad ?: p.headingRad, confidence = 1f, sign = null)
            }
            ArTracking.PAUSED -> p.copy(confidence = decayed(s.timeMs))
            ArTracking.STOPPED -> p.copy(confidence = 0f)
        }
    }

    private fun decayed(nowMs: Long): Float {
        val lostS = (nowMs - lastTrackingMs).coerceAtLeast(0) / 1000.0
        return (1.0 - DECAY_PER_S * lostS).coerceIn(0.0, 1.0).toFloat()
    }

    /** The AR view left (or its session failed): stop claiming a live position. */
    fun markGone() {
        _live.value = false
        lastState = null
    }

    override fun follow(route: Route) = Unit // Position comes from the camera, not the route.

    override fun stop() = markGone()

    companion object {
        const val DECAY_PER_S = 0.25
    }
}
