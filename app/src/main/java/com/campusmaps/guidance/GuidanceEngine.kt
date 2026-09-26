package com.campusmaps.guidance

import com.campusmaps.data.model.Point
import com.campusmaps.route.Formats
import com.campusmaps.route.LockedNotice
import com.campusmaps.route.Route
import com.campusmaps.route.RouteStep
import com.campusmaps.route.StepKind
import com.campusmaps.shared.WatchStep
import com.campusmaps.shared.WatchStepType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Where the student is along the route.
data class Progress(
    val stepIndex: Int = 0,
    val alongM: Double = 0.0,      // Metres walked along the route
    val segmentIndex: Int = 0,     // Route segment we are on (points[i] -> points[i + 1])
    val offRouteM: Double = 0.0,   // How far the student is from the route line
    val arrived: Boolean = false,
    // When the current step became current (caller's clock, ms). Null when the caller passes no time.
    val stepShownAtMs: Long? = null,
)

// Pure logic, no Android: matches a pose to the route and decides the current step.
// Unit tested in GuidanceEngineTest.
object GuidanceEngine {

    // A step counts as done this close to its finish point.
    private const val COMPLETE_SLACK_M = 1.5
    // How many segments ahead we look when matching the pose to the route.
    private const val LOOK_AHEAD = 8
    // When starting outside, the "Walk to" step switches to "Go through" this close to the door.
    const val APPROACH_M = 8.0
    // Further than this from the route line means the student went another way.
    const val OFF_ROUTE_M = 6.0
    // An "Exit toward" step is usually 3 m before a turn (docs/21 open issue 6, docs/22 #16). It stays up until the
    // student is past its end point, and for at least this long, so it is read instead of flashing.
    const val EXIT_MIN_SHOW_MS = 2_500L
    // The step right after an exit (core's "Turn left at Elevator lobby", 1.5 m later on CS) would otherwise be passed
    // the moment the exit is released. It gets its own short moment; its distance rule is unchanged, so it never stalls.
    const val AFTER_EXIT_MIN_SHOW_MS = 1_500L

    // nowMs: the caller's clock (ms), only used for the exit step's minimum time on screen. Null skips that rule.
    fun update(route: Route, pose: Pose, previous: Progress, nowMs: Long? = null): Progress {
        val points = route.points
        if (points.size < 2) {
            return previous.copy(stepIndex = route.steps.lastIndex, arrived = true)
        }

        // 1. Snap the pose onto the nearest nearby segment.
        var bestDistance = Double.MAX_VALUE
        var bestAlong = previous.alongM
        var bestSegment = previous.segmentIndex
        val from = max(0, previous.segmentIndex - 1)
        val to = min(points.size - 2, previous.segmentIndex + LOOK_AHEAD)
        for (i in from..to) {
            val a = points[i]
            val b = points[i + 1]
            val (distance, along) = if (a.floor == b.floor) {
                if (pose.floor != a.floor) continue
                val (d, t) = projectOntoSegment(pose.position, a.position, b.position)
                d to (a.cumulativeM + t * (b.cumulativeM - a.cumulativeM))
            } else {
                // Elevator / stairs hop: counts if we are in the shaft between those floors.
                val low = min(a.floor, b.floor)
                val high = max(a.floor, b.floor)
                if (pose.floor !in low..high) continue
                pose.position.distanceTo(a.position) to a.cumulativeM
            }
            // "<=" prefers later segments on ties, so we move forward at corners and shafts.
            if (distance <= bestDistance + 1e-6) {
                bestDistance = distance
                bestAlong = along
                bestSegment = i
            }
        }
        val offRoute = if (bestDistance == Double.MAX_VALUE) Double.MAX_VALUE else bestDistance

        // 2. Move the step index forward past every step that is complete.
        var stepIndex = previous.stepIndex
        var arrived = previous.arrived
        var shownAt = if (nowMs == null) null else previous.stepShownAtMs ?: nowMs
        while (stepIndex < route.steps.size) {
            val step = route.steps[stepIndex]
            val exit = step.kind == StepKind.EXIT_TOWARD
            val slack = if (exit) 0.0 else COMPLETE_SLACK_M
            val minShowMs = when {
                exit -> EXIT_MIN_SHOW_MS
                route.steps.getOrNull(stepIndex - 1)?.kind == StepKind.EXIT_TOWARD -> AFTER_EXIT_MIN_SHOW_MS
                else -> 0L
            }
            val shownLongEnough = nowMs == null || shownAt == null || nowMs - shownAt >= minShowMs
            val done = bestAlong >= step.completeAtM - slack - 1e-6 &&
                shownLongEnough &&
                pose.floor == step.completeFloor &&
                bestSegment >= min(step.startIndex, points.size - 2) - 1
            if (!done) break
            if (stepIndex == route.steps.lastIndex) {
                if (step.kind == StepKind.ARRIVE || step.kind == StepKind.ALREADY_THERE) arrived = true
                break
            }
            stepIndex++
            shownAt = nowMs // The next step became current now.
        }

        return Progress(
            stepIndex = stepIndex,
            alongM = bestAlong,
            segmentIndex = bestSegment,
            offRouteM = offRoute,
            arrived = arrived,
            stepShownAtMs = shownAt,
        )
    }

    // Metres left until the current step's manoeuvre (the "in 16 m").
    fun distanceToStep(route: Route, step: RouteStep, progress: Progress): Double {
        val target = route.points.getOrNull(step.startIndex)?.cumulativeM ?: 0.0
        return max(0.0, target - progress.alongM)
    }

    // Banner text for the current step. "Walk to X" turns into "Go through X" near the door.
    fun bannerText(step: RouteStep, distanceM: Double): String =
        if (step.kind == StepKind.WALK_TO_ENTRANCE && step.approachText != null && distanceM <= APPROACH_M) {
            step.approachText
        } else {
            step.text
        }

    // What the watch shows for this step (section 12 table).
    fun watchStep(step: RouteStep, distanceM: Double, lockedNotice: LockedNotice?, showLocked: Boolean): WatchStep {
        if (showLocked && lockedNotice != null && lockedNotice.usingEntrance != null) {
            return WatchStep(WatchStepType.LOCKED, lockedNotice.usingEntrance, "${lockedNotice.lockedEntrance.substringBefore(' ')} locked")
        }
        val metres = Formats.metres(distanceM)
        val place = step.place.orEmpty()
        return when (step.kind) {
            StepKind.TURN_LEFT, StepKind.TURN_AROUND -> WatchStep(WatchStepType.LEFT, metres, place)
            StepKind.TURN_RIGHT -> WatchStep(WatchStepType.RIGHT, metres, place)
            // Street steps (Explore start): the arrow from Google's maneuver, the banner's text as the label.
            StepKind.STREET_LEFT -> WatchStep(WatchStepType.LEFT, metres, place)
            StepKind.STREET_RIGHT -> WatchStep(WatchStepType.RIGHT, metres, place)
            StepKind.STREET_STRAIGHT -> WatchStep(WatchStepType.STRAIGHT, metres, place)
            StepKind.ELEVATOR -> WatchStep(WatchStepType.ELEVATOR, Formats.floorLong(step.targetFloor ?: 0), "Elevator")
            StepKind.STAIRS -> {
                // completeFloor equals targetFloor for rides, so compare against where the ride starts.
                val up = (step.targetFloor ?: 0) >= (step.fromFloor ?: step.completeFloor)
                if (up) WatchStep(WatchStepType.STAIRS, Formats.floorLong(step.targetFloor ?: 0), "Stairs up")
                else WatchStep(WatchStepType.STAIRS_DOWN, Formats.floorLong(step.targetFloor ?: 0), "Stairs down")
            }
            StepKind.WALK_TO_ENTRANCE ->
                if (distanceM <= APPROACH_M) WatchStep(WatchStepType.DOOR, place, "Go through")
                else WatchStep(WatchStepType.STRAIGHT, metres, place)
            StepKind.DOOR -> WatchStep(WatchStepType.DOOR, place, "Go through")
            StepKind.ARRIVE, StepKind.ALREADY_THERE -> WatchStep(WatchStepType.ARRIVED, place, "Arrived")
            StepKind.HEAD, StepKind.CONTINUE, StepKind.EXIT_TOWARD -> WatchStep(WatchStepType.STRAIGHT, metres, place)
        }
    }

    // Distance from p to segment a-b, and how far along (0..1) the closest point is.
    fun projectOntoSegment(p: Point, a: Point, b: Point): Pair<Double, Double> {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 1e-9) return p.distanceTo(a) to 0.0
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq).coerceIn(0.0, 1.0)
        val closest = Point(a.x + t * dx, a.y + t * dy)
        return hypot(p.x - closest.x, p.y - closest.y) to t
    }
}
