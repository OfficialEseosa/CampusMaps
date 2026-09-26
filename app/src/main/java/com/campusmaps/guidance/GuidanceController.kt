package com.campusmaps.guidance

import com.campusmaps.data.model.Building
import com.campusmaps.data.model.GraphEdge
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.NodeKind
import com.campusmaps.platform.Speaker
import com.campusmaps.platform.WatchBridge
import com.campusmaps.routing.LockedNotice
import com.campusmaps.routing.Route
import com.campusmaps.routing.RouteStep
import com.campusmaps.routing.Router
import com.campusmaps.routing.StepKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

// Everything S2 and S3 need to draw one frame.
data class GuidanceState(
    val building: Building,
    val route: Route,
    val destination: GraphNode,
    val step: RouteStep,
    val nextStep: RouteStep?,
    val bannerText: String,
    val distanceToStepM: Double,
    val pose: Pose,
    val progress: Progress,
    val locating: Boolean,         // Confidence below 0.5 and not arrived
    val arrived: Boolean,
    val rerouteCount: Int,
    val showRerouteChip: Boolean,  // True for about 3 s after a reroute
) {
    val floor: Int get() = pose.floor
    val startsOutside: Boolean get() = step.kind == StepKind.WALK_TO_ENTRANCE
}

// Runs one guidance session: follows the pose, advances steps, speaks, mirrors to the watch,
// and reroutes when the student goes another way. S2 (AR) and S3 (glasses) share it.
class GuidanceController(
    private val scope: CoroutineScope,
    private val building: Building,
    firstRoute: Route,
    private val lockedNotice: LockedNotice?,
    private val router: Router,
    private val now: () -> LocalDateTime,
    private val avoidStairs: () -> Boolean,
    private val extraEdges: () -> List<GraphEdge>,
    private val speaker: Speaker,
    private val speakEnabled: () -> Boolean,
    private val watch: WatchBridge,
) {
    private var route: Route = firstRoute
    private var progress = Progress()
    private var rerouteCount = 0
    private var lastRerouteAt = 0L
    private var chipUntil = 0L
    private var lastSpokenStep = -1
    private var loopJob: Job? = null
    private val startedAt = System.currentTimeMillis()

    // When true, S3 is on screen and the glasses cycle does the speaking.
    var glassesMode: Boolean = false

    private val simulator = SimulatedPositionProvider(
        scope = scope,
        startPose = Pose(
            position = firstRoute.points.first().position,
            floor = firstRoute.points.first().floor,
            headingRad = initialHeading(firstRoute),
            confidence = if (firstRoute.startsOutside) 0.3f else 0.92f,
        ),
        startLost = firstRoute.startsOutside,
        signText = { nearestSignText() },
    )
    val position: PositionProvider = simulator
    val simulation: SimulationControls = simulator

    private val _state = MutableStateFlow(buildState(position.pose.value))
    val state: StateFlow<GuidanceState> = _state.asStateFlow()

    fun start() {
        watch.reset()
        position.follow(route)
        loopJob = scope.launch {
            while (true) {
                tick()
                delay(100)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        position.stop()
        speaker.stop()
    }

    // "Repeat" on S3, and the debug overlay: say the current instruction again.
    fun repeat() {
        speaker.speak(_state.value.bannerText)
    }

    // Debug "Skip step" and S3 "Fake step": jump to the end of the current step.
    fun skipStep() {
        val step = _state.value.step
        val targetIndex = route.points.indexOfLast { it.cumulativeM <= step.completeAtM + 0.01 && it.floor == step.completeFloor }
        simulation.jumpToPoint(maxOf(targetIndex, step.startIndex))
    }

    private fun tick() {
        val pose = position.pose.value
        progress = GuidanceEngine.update(route, pose, progress)

        // Reroute when clearly off the path (and not while we are unsure where we are).
        val nowMs = System.currentTimeMillis()
        if (!progress.arrived &&
            pose.confidence >= LOCATE_CONFIDENCE &&
            progress.offRouteM > GuidanceEngine.OFF_ROUTE_M &&
            nowMs - lastRerouteAt > 3_000
        ) {
            reroute(pose)
            lastRerouteAt = nowMs
        }

        val state = buildState(pose)
        _state.value = state

        // Say each new instruction once (S2 only; S3's glasses cycle speaks on its own).
        if (state.progress.stepIndex != lastSpokenStep && pose.confidence >= LOCATE_CONFIDENCE) {
            lastSpokenStep = state.progress.stepIndex
            if (speakEnabled() && !glassesMode) {
                speaker.speak(if (state.arrived) state.step.text else state.bannerText)
            }
        }

        // Show the locked entrance on the watch for the first 4 seconds, then the steps.
        val showLocked = nowMs - startedAt < 4_000
        watch.send(GuidanceEngine.watchStep(state.step, state.distanceToStepM, lockedNotice, showLocked))
    }

    private fun reroute(pose: Pose) {
        val start = nearestNode(pose) ?: return
        val newRoute = router.bestRoute(
            building = building,
            startId = start.id,
            destinationId = route.destination.id,
            time = now(),
            avoidStairs = avoidStairs(),
            extraEdges = extraEdges(),
            startHeadingRad = pose.headingRad,
        ) ?: return
        route = newRoute
        progress = Progress()
        rerouteCount++
        chipUntil = System.currentTimeMillis() + 3_000
        lastSpokenStep = -1
        position.follow(newRoute)
    }

    private fun buildState(pose: Pose): GuidanceState {
        val step = route.steps[progress.stepIndex.coerceIn(0, route.steps.lastIndex)]
        val next = route.steps.getOrNull(progress.stepIndex + 1)
        val distance = GuidanceEngine.distanceToStep(route, step, progress)
        return GuidanceState(
            building = building,
            route = route,
            destination = route.destination,
            step = step,
            nextStep = next,
            bannerText = GuidanceEngine.bannerText(step, distance),
            distanceToStepM = distance,
            pose = pose,
            progress = progress,
            locating = pose.confidence < LOCATE_CONFIDENCE && !progress.arrived,
            arrived = progress.arrived,
            rerouteCount = rerouteCount,
            showRerouteChip = System.currentTimeMillis() < chipUntil,
        )
    }

    // The closest walkable node on the student's floor (same inside / outside as now).
    private fun nearestNode(pose: Pose): GraphNode? {
        val outside = route.points.getOrNull(progress.segmentIndex)?.node?.isOutdoor == true
        return building.nodes.values
            .filter { it.floor == pose.floor && it.kind != NodeKind.ROOM && (it.isOutdoor == outside || it.kind == NodeKind.ENTRANCE) }
            .minByOrNull { it.position.distanceTo(pose.position) }
    }

    // The sign closest to the student, for the simulated "Reading sign..." moment.
    private fun nearestSignText(): String {
        val pose = position.pose.value
        return building.nodes.values
            .filter { it.signText != null && it.floor == pose.floor }
            .minByOrNull { it.position.distanceTo(pose.position) }
            ?.signText ?: route.destination.name.uppercase()
    }

    private fun initialHeading(route: Route): Double {
        val a = route.points.getOrNull(0)?.position ?: return 0.0
        val b = route.points.getOrNull(1)?.position ?: return 0.0
        return kotlin.math.atan2(b.y - a.y, b.x - a.x)
    }
}
