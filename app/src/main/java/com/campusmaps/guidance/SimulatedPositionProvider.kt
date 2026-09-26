package com.campusmaps.guidance

import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.Point
import com.campusmaps.routing.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// A pretend student who walks the route at normal speed. Lets the whole guidance flow
// (steps, elevator ride, locate me, reroute, arrival) run on the emulator or at a desk.
//
// Elevator and stairs are sped up (a few seconds instead of a real wait) so demos stay short.
class SimulatedPositionProvider(
    private val scope: CoroutineScope,
    startPose: Pose,
    // When starting outside, pretend we first need a sign to lock on (shows the Locate me state).
    private val startLost: Boolean,
    // What the pretend camera "reads" while locating (the nearest sign to the student).
    private val signText: () -> String,
) : PositionProvider, SimulationControls {

    private data class Waypoint(val position: Point, val floor: Int, val arrivedBy: EdgeKind)

    private val _pose = MutableStateFlow(startPose)
    override val pose: StateFlow<Pose> = _pose.asStateFlow()

    override var speedMultiplier: Double = 1.0

    private var waypoints: List<Waypoint> = emptyList()
    private var index = 0           // Next waypoint we are heading to
    private var leadIn = 0          // 1 when an extra "walk onto the route" waypoint was added
    private var rideTimer = 0.0     // Seconds spent on the current elevator / stairs hop
    private var lostTimer = -1.0    // >= 0 while "lost" (confidence low)
    private var job: Job? = null

    init {
        if (startLost) lostTimer = 0.0
    }

    override fun follow(route: Route) {
        val current = _pose.value
        val points = route.points.map { Waypoint(it.position, it.floor, it.arrivedBy) }
        // Walk from wherever we are now onto the new route.
        val needsLeadIn = points.isNotEmpty() &&
            (points.first().floor != current.floor || points.first().position.distanceTo(current.position) > 0.5)
        leadIn = if (needsLeadIn) 1 else 0
        waypoints = if (needsLeadIn) listOf(Waypoint(current.position, current.floor, EdgeKind.WALK)) + points else points
        index = if (waypoints.size > 1) 1 else 0
        rideTimer = 0.0
        if (job == null) job = scope.launch { loop() }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    override fun dropConfidence() {
        lostTimer = 0.0
    }

    // Steps 8 m sideways off the path, so guidance notices and reroutes.
    override fun pushOffRoute() {
        val p = _pose.value
        val side = Point(-sin(p.headingRad), cos(p.headingRad))
        _pose.value = p.copy(position = p.position + side * 8.0)
        // Keep walking toward the same next waypoint from the new spot.
    }

    // index is a Route.points index.
    override fun jumpToPoint(index: Int) {
        val target = (index + leadIn).coerceIn(0, waypoints.lastIndex.coerceAtLeast(0))
        val w = waypoints.getOrNull(target) ?: return
        this.index = min(target + 1, waypoints.lastIndex)
        rideTimer = 0.0
        _pose.value = _pose.value.copy(position = w.position, floor = w.floor)
    }

    private suspend fun loop() {
        val tickMs = 50L
        while (scope.isActive) {
            delay(tickMs)
            step(tickMs / 1000.0)
        }
    }

    private fun step(dt: Double) {
        var p = _pose.value

        // Lost / locate me simulation: 1.5 s of nothing, then a sign is read, then we recover.
        if (lostTimer >= 0) {
            lostTimer += dt
            val sign = if (lostTimer > 1.5) SignSighting(signText(), 0.06f, 0.19f, 0.94f, 0.26f) else null
            if (lostTimer > 4.0) {
                lostTimer = -1.0
                _pose.value = p.copy(confidence = 0.92f, sign = null)
            } else {
                _pose.value = p.copy(confidence = 0.3f, sign = sign)
            }
            return // Standing still while pointing at a sign
        }
        if (p.confidence < 0.9f || p.sign != null) p = p.copy(confidence = 0.92f, sign = null)

        if (waypoints.isEmpty() || index >= waypoints.size || (index == waypoints.lastIndex && atWaypoint(p, waypoints.last()))) {
            _pose.value = p
            return
        }

        val target = waypoints[index]
        if (target.arrivedBy != EdgeKind.WALK) {
            // Riding: wait (elevator only, first floor of a ride), then one floor per hop.
            val firstHop = waypoints[index - 1].arrivedBy != target.arrivedBy
            val wait = if (target.arrivedBy == EdgeKind.ELEVATOR && firstHop) 2.5 else 0.0
            val perFloor = if (target.arrivedBy == EdgeKind.ELEVATOR) 1.2 else 2.0
            rideTimer += dt * speedMultiplier
            if (rideTimer >= wait + perFloor) {
                rideTimer = 0.0
                p = p.copy(position = target.position, floor = target.floor)
                index = min(index + 1, waypoints.lastIndex)
            }
            _pose.value = p
            return
        }

        // Walking toward the target at 1.3 m/s.
        val toTarget = target.position - p.position
        val remaining = toTarget.let { kotlin.math.hypot(it.x, it.y) }
        val stride = 1.3 * dt * speedMultiplier
        val desiredHeading = if (remaining > 0.01) atan2(toTarget.y, toTarget.x) else p.headingRad
        val heading = turnToward(p.headingRad, desiredHeading, maxStep = PI * dt * speedMultiplier)
        p = if (stride >= remaining) {
            index = min(index + 1, waypoints.lastIndex)
            p.copy(position = target.position, floor = target.floor, headingRad = heading)
        } else {
            val t = stride / remaining
            p.copy(position = p.position + toTarget * t, headingRad = heading)
        }
        _pose.value = p
    }

    private fun atWaypoint(p: Pose, w: Waypoint) = p.floor == w.floor && p.position.distanceTo(w.position) < 0.05

    // Turns smoothly instead of snapping, so the AR arrows swing naturally at corners.
    private fun turnToward(from: Double, to: Double, maxStep: Double): Double {
        var delta = to - from
        while (delta > PI) delta -= 2 * PI
        while (delta < -PI) delta += 2 * PI
        return if (abs(delta) <= maxStep) to else from + maxStep * kotlin.math.sign(delta)
    }
}
