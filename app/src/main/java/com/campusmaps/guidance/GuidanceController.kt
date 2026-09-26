package com.campusmaps.guidance

import com.campusmaps.data.model.Building
import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.GraphEdge
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.NodeKind
import com.campusmaps.platform.Speaker
import com.campusmaps.platform.WatchBridge
import com.campusmaps.route.LockedNotice
import com.campusmaps.route.Route
import com.campusmaps.route.RouteStep
import com.campusmaps.route.CoreRouter
import com.campusmaps.route.FloorChange
import com.campusmaps.route.StepKind
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
    // True when distanceToStepM is the GPS distance to an outdoor step's end (Explore start, fresh fix).
    val gpsDistance: Boolean = false,
) {
    val floor: Int get() = pose.floor
    // Street steps and the "Enter" step of an Explore start are outdoor too (RouteStep.outdoorEnd).
    val startsOutside: Boolean get() = step.kind == StepKind.WALK_TO_ENTRANCE || step.outdoorEnd != null
}

// Runs one guidance session: follows the pose, advances steps, speaks, mirrors to the watch,
// and reroutes when the student goes another way. S2 (AR) and S3 (glasses) share it.
class GuidanceController(
    private val scope: CoroutineScope,
    private val building: Building,
    firstRoute: Route,
    private val lockedNotice: LockedNotice?,
    private val router: CoreRouter,
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
    // "Locate me" shows after 2 s of low confidence and hides after 1 s of good confidence, so it does not blink (qa-phone N5).
    private val locate = LocateHysteresis()
    private var rawLocating = false
    private var loopJob: Job? = null
    private val startedAt = System.currentTimeMillis()

    // Latest FusedLocation fix (MainViewModel feeds it for Explore starts); outdoor steps follow it (OutdoorGps).
    @Volatile private var lastFix: com.campusmaps.geo.LocationFix? = null
    // Debug Step / Skip on an outdoor step: move exactly one step on the next tick.
    @Volatile private var forceNext = false

    fun onFix(fix: com.campusmaps.geo.LocationFix) { lastFix = fix }

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

    // Building -> ARCore world transform for this session (docs/05): set by ArGuidanceView's sign fix (Augmented Images)
    // or its debug "Place route here" floor tap. Null = not aligned yet. Cleared when the AR view leaves.
    val buildingToWorld = MutableStateFlow<com.campusmaps.loc.BuildingToWorld?>(null)

    // Position source (docs/03 section 1): the ARCore camera pose once the route is placed, else the simulator.
    // Debug moves (Step, Walk, jump, continuous walker) switch to the simulator; see loc/SwitchablePositionProvider.
    private val arPosition = com.campusmaps.loc.ArPositionProvider(transform = { buildingToWorld.value }, initial = simulator.pose.value)
    val positionSource = com.campusmaps.loc.SwitchablePositionProvider(scope, simulator, arPosition, buildingToWorld)
    val position: PositionProvider = positionSource
    val simulation: SimulationControls = positionSource

    init {
        com.campusmaps.loc.ArFeed.setBuilding(building.core)
    }

    // Barometer floor (loc/baro, docs/03 section 3). Null when the device has no barometer: the simulator's timed rides stay.
    private var baro: com.campusmaps.loc.baro.BarometerFloorTracker? = null
    private var baroFixJob: Job? = null
    private var baroCommitAtMs = 0L
    private var holdSinceMs = 0L      // When the simulator started standing in the elevator waiting for the barometer
    private var holdGaveUp = false    // The pressure did not move: let the simulator ride on its timer
    private var holdStep = -1

    private val _state = MutableStateFlow(buildState(position.pose.value))
    val state: StateFlow<GuidanceState> = _state.asStateFlow()

    fun start() {
        watch.reset()
        com.campusmaps.loc.ArFeed.attach(arPosition)
        positionSource.start(arExpected = com.campusmaps.loc.ArFeed.arExpected && !glassesMode)
        position.follow(route)
        startBarometer()
        loopJob = scope.launch {
            while (true) {
                tick()
                delay(100)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        baroFixJob?.cancel()
        baro?.stop()
        baro = null
        simulator.holdRides = false
        com.campusmaps.loc.ArFeed.detach(arPosition)
        position.stop()
        speaker.stop()
    }

    // "Repeat" on S3, and the debug overlay: say the current instruction again.
    fun repeat() {
        speaker.speak(_state.value.bannerText)
    }

    // Debug "Skip step" and S3 "Fake step": jump to the end of the current step.
    fun skipStep() {
        if (OutdoorGps.isOutdoor(_state.value.step)) { forceNext = true; return }
        val step = _state.value.step
        val targetIndex = route.points.indexOfLast { it.cumulativeM <= step.completeAtM + 0.01 && it.floor == step.completeFloor }
        simulation.jumpToPoint(maxOf(targetIndex, step.startIndex))
    }

    private fun tick() {
        val pose = position.pose.value
        val nowMs = System.currentTimeMillis()
        val before = progress
        progress = GuidanceEngine.update(route, pose, progress, nowMs)
        followOutdoorGps(before, nowMs)
        followBarometer(before, pose, nowMs)

        // Reroute when clearly off the path (and not while we are unsure where we are).
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
        // The first one is prefixed with core's locked-entrance notice (Demo C: "Heads up: ... Using West entrance instead.").
        if (state.progress.stepIndex != lastSpokenStep && (pose.confidence >= LOCATE_CONFIDENCE || state.gpsDistance)) {
            val first = lastSpokenStep == -1 && rerouteCount == 0
            lastSpokenStep = state.progress.stepIndex
            if (speakEnabled() && !glassesMode) {
                val text = if (state.arrived) state.step.text else state.bannerText
                speaker.speak(if (first && lockedNotice != null) "Heads up: ${lockedNotice.text} $text" else text)
            }
        }

        // Show the locked entrance on the watch for the first 4 seconds, then the steps.
        val showLocked = nowMs - startedAt < 4_000
        watch.send(GuidanceEngine.watchStep(state.step, state.distanceToStepM, lockedNotice, showLocked))
    }

    // Outdoor steps (Explore start): GPS completes them, not the walker, while the fix is fresh; the debug Step forces one.
    // Past the last outdoor step the simulator stands at the entrance, so the indoor engine carries on as when the
    // walker reaches the door.
    private fun followOutdoorGps(before: Progress, nowMs: Long) {
        val prevStep = route.steps.getOrNull(before.stepIndex) ?: return
        if (!OutdoorGps.isOutdoor(prevStep)) return
        val force = forceNext
        forceNext = false
        val fix = lastFix
        val next = OutdoorGps.nextIndex(route, before.stepIndex, progress.stepIndex, fix, nowMs, before.stepShownAtMs ?: nowMs, force)
        if (next == progress.stepIndex && !force && !OutdoorGps.fresh(fix, nowMs)) return // the walker's answer stands
        val moved = next != before.stepIndex
        progress = progress.copy(
            stepIndex = next,
            arrived = false,
            stepShownAtMs = if (moved) nowMs else before.stepShownAtMs ?: nowMs,
        )
        if (moved) {
            val why = if (force) "debug step" else "GPS ${OutdoorGps.distanceM(prevStep, fix)?.let { "%.0f m".format(it) }}"
            runCatching { android.util.Log.i("Outdoor", "step ${before.stepIndex} done ($why): ${prevStep.text}") }
            if (!OutdoorGps.isOutdoor(route.steps.getOrNull(next))) {
                runCatching { android.util.Log.i("Outdoor", "entrance reached: indoor steps from ${route.points.getOrNull(prevStep.startIndex)?.node?.id}") }
                simulation.jumpToPoint(prevStep.startIndex)
                // Stand at the entrance for this tick's banner and watch too (the jump reaches the pose on the next tick).
                val at = prevStep.startIndex.coerceIn(0, route.points.lastIndex)
                progress = progress.copy(alongM = maxOf(progress.alongM, route.points[at].cumulativeM),
                    segmentIndex = maxOf(progress.segmentIndex, (at).coerceAtMost(route.points.size - 2).coerceAtLeast(0)))
            }
        }
    }

    private fun startBarometer() {
        val startFloor = position.pose.value.floor
        val b = com.campusmaps.loc.baro.BaroFeed.newTracker(building.core.floorHeightM) { f -> onBarometerFloor(f) } ?: run {
            runCatching { android.util.Log.i("Baro", "no barometer: elevator and stairs rides use the simulator's timer") }
            return
        }
        if (!b.start(startFloor)) return
        baro = b
        positionSource.barometerFloorKnown(startFloor)
        // A sign fix or a "Place route here" floor tap puts the student on a known floor: re-zero there.
        baroFixJob = scope.launch {
            buildingToWorld.collect { t ->
                if (t != null) {
                    b.rezero(t.refFloor, "sign fix / floor tap")
                    positionSource.barometerFloorKnown(t.refFloor)
                }
            }
        }
    }

    // Rides follow the barometer: the step's ride gate, the simulator's hold, and the re-zero on known floors.
    private fun followBarometer(before: Progress, pose: Pose, nowMs: Long) {
        val b = baro ?: return
        val step = route.steps.getOrNull(progress.stepIndex)
        val ride = step?.kind == StepKind.ELEVATOR || step?.kind == StepKind.STAIRS
        if (ride) b.rideStarted() else b.rideEnded()

        // The pretend student waits in the car for the pressure; if it does not move for 10 s, the timer rides instead.
        if (progress.stepIndex != holdStep) { holdStep = progress.stepIndex; holdSinceMs = 0L; holdGaveUp = false }
        if (ride && b.ready && simulator.riding) {
            if (holdSinceMs == 0L) holdSinceMs = nowMs
            if (!holdGaveUp && nowMs - holdSinceMs > HOLD_GIVE_UP_MS && b.movedSinceRideStart() < 0.15) {
                holdGaveUp = true
                runCatching { android.util.Log.i("Baro", "pressure still after ${HOLD_GIVE_UP_MS / 1000} s in the ride: simulator's timer rides instead") }
            }
        }
        val hold = ride && b.ready && !holdGaveUp
        if (simulator.holdRides != hold) {
            simulator.holdRides = hold
            runCatching { android.util.Log.i("Baro", if (hold) "ride: floor follows the barometer" else "ride hold off") }
        }

        // A hallway step done on a known floor: re-zero (weather drift).
        if (progress.stepIndex > before.stepIndex && !b.gateOpen()) {
            val done = route.steps.getOrNull(before.stepIndex)
            if (done != null && done.kind != StepKind.ELEVATOR && done.kind != StepKind.STAIRS && !OutdoorGps.isOutdoor(done)) {
                b.rezero(pose.floor, "step done on foot")
            }
        }
        // Something else set the floor (debug jump, timed ride, camera height): believe it and re-zero there.
        if (pose.floor != b.floor && nowMs - baroCommitAtMs > 1_000) {
            b.rezero(pose.floor, "floor ${pose.floor} set by the position source")
            positionSource.barometerFloorKnown(pose.floor)
        }
    }

    private fun onBarometerFloor(floor: Int) {
        baroCommitAtMs = System.currentTimeMillis()
        // The ride's node on that floor, nearest to where we are on the route.
        val pts = route.points
        val at = progress.segmentIndex
        val idx = pts.indices
            .filter { i -> pts[i].floor == floor && (pts[i].arrivedBy != EdgeKind.WALK || pts.getOrNull(i + 1)?.arrivedBy.let { it != null && it != EdgeKind.WALK }) }
            .filter { i -> i >= at - 1 && i <= at + 12 }
            .minByOrNull { kotlin.math.abs(it - at) }
        runCatching { android.util.Log.i("Baro", "floor $floor -> position (route point ${idx ?: "none"}${idx?.let { " " + pts[it].node.id } ?: ""})") }
        positionSource.setBarometerFloor(floor, idx)
    }

    private fun reroute(pose: Pose) {
        val start = nearestNode(pose) ?: return
        // The route node walked from last: core turns it into "Turn left/right toward X" (Start.AtNode.cameFrom).
        val cameFrom = route.points.getOrNull(progress.segmentIndex)?.node?.takeIf { !it.isOutdoor && it.id != start.id }?.id
        val newRoute = router.bestRoute(
            building = building,
            startId = start.id,
            destinationId = route.destination.id,
            time = now(),
            avoidStairs = avoidStairs(),
            extraEdges = extraEdges(),
            cameFromId = cameFrom,
            preferMethod = methodOf(route),
        ) ?: return
        adopt(newRoute)
    }

    // Debug "jump to node / start point" (Raphael's FakeLocalizer.jumpTo): on the route, move along it;
    // off the route (inside or outside), put the student there and reroute from that node.
    fun jumpTo(nodeId: String) {
        val node = building.nodes[nodeId] ?: return
        val onRoute = route.points.indexOfFirst { it.node.id == nodeId }
        if (onRoute >= 0) {
            simulation.jumpToPoint(onRoute)
            return
        }
        simulation.placeAt(node.position, node.floor)
        val newRoute = router.bestRoute(
            building = building,
            startId = nodeId,
            destinationId = route.destination.id,
            time = now(),
            avoidStairs = avoidStairs(),
            extraEdges = extraEdges(),
            cameFromId = route.points.getOrNull(progress.segmentIndex)?.node?.takeIf { !it.isOutdoor }?.id,
            preferMethod = methodOf(route),
        ) ?: return
        lastRerouteAt = System.currentTimeMillis()
        adopt(newRoute)
    }

    // Debug fake walk "Step": move to the next node of the route (Raphael's one-node step).
    fun stepToNextNode() {
        if (OutdoorGps.isOutdoor(_state.value.step)) { forceNext = true; return }
        val next = (progress.segmentIndex + 1).coerceAtMost(route.points.lastIndex)
        simulation.jumpToPoint(next)
    }

    private fun methodOf(r: Route): FloorChange = when {
        r.points.any { it.arrivedBy == EdgeKind.ELEVATOR } -> FloorChange.ELEVATOR
        r.points.any { it.arrivedBy == EdgeKind.STAIRS } -> FloorChange.STAIRS
        else -> FloorChange.LEVEL
    }

    private fun adopt(newRoute: Route) {
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
        val nowMs = System.currentTimeMillis()
        val fix = lastFix
        val distance = OutdoorGps.displayDistanceM(step, fix, nowMs, progress.alongM, GuidanceEngine.distanceToStep(route, step, progress))
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
            locating = locatingNow(pose) && !progress.arrived,
            arrived = progress.arrived,
            rerouteCount = rerouteCount,
            showRerouteChip = System.currentTimeMillis() < chipUntil,
            gpsDistance = OutdoorGps.isOutdoor(step) && OutdoorGps.shown(fix, nowMs),
        )
    }

    private fun locatingNow(pose: Pose): Boolean {
        val raw = pose.confidence < LOCATE_CONFIDENCE && !progress.arrived
        if (raw != rawLocating) {
            rawLocating = raw
            runCatching {
                android.util.Log.d("Position", "confidence ${"%.2f".format(pose.confidence)} ${if (raw) "below" else "above"} $LOCATE_CONFIDENCE " +
                    "(source ${if (positionSource.usingAr) "AR" else "simulator"})")
            }
        }
        return locate.update(raw, System.currentTimeMillis())
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

    private companion object {
        const val HOLD_GIVE_UP_MS = 10_000L
    }

    private fun initialHeading(route: Route): Double {
        val a = route.points.getOrNull(0)?.position ?: return 0.0
        val b = route.points.getOrNull(1)?.position ?: return 0.0
        return kotlin.math.atan2(b.y - a.y, b.x - a.x)
    }
}
