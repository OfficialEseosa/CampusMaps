package com.campusmaps.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campusmaps.AppContainer
import com.campusmaps.data.AppSettings
import com.campusmaps.data.ClockMode
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.NodeKind
import com.campusmaps.guidance.GuidanceController
import com.campusmaps.platform.ArOverride
import com.campusmaps.platform.TtsStatus
import com.campusmaps.route.Formats
import com.campusmaps.route.Route
import com.campusmaps.route.RouteOption
import com.campusmaps.route.RoutePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.campusmaps.data.AppClock
import java.time.DayOfWeek
import java.time.LocalTime

// Debug fake-walk state (Raphael's controls).
data class WalkState(
    val auto: Boolean = false,        // Walk: one node every intervalSec
    val intervalSec: Int = 3,
    val paused: Boolean = false,      // Continuous simulated walker paused
    val lowConfidence: Boolean = false,
)

// The screens from the flow in section 3 of the handoff.
enum class Screen { DESTINATION, ROUTES, GUIDANCE, GLASSES, ADD_SHORTCUT, EXPLORE }

// What the user has picked on S1. Kept apart from settings because Reset clears it.
data class Selection(
    val destinationId: String? = null,
    val startId: String? = null, // Null = the building's default start
    val query: String = "",
    // The phone's fix when the trip was started from the Explore map: adds the "Your location" start node (CoreBridge.withGpsStart).
    val gps: com.campusmaps.data.model.OutdoorStart? = null,
)

// Everything S1 and S1b draw.
data class TripUiState(
    val settings: AppSettings = AppSettings(),
    val building: Building,
    val destination: GraphNode? = null,
    val start: GraphNode,
    val startOptions: List<GraphNode>,
    val query: String = "",
    val searchHits: List<GraphNode> = emptyList(),
    val destinationRows: List<GraphNode> = emptyList(),
    val plan: RoutePlan? = null,
    val simulatedTimeLabel: String? = null, // "Sat 21:00" when the clock is simulated
) {
    val startsInside: Boolean get() = !start.isOutdoor
    val routeError: String? get() = (plan as? RoutePlan.NoRoute)?.message
}

class MainViewModel(private val app: AppContainer) : ViewModel() {

    private val _screen = MutableStateFlow(Screen.DESTINATION)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    // Double-tap guard (Raphael's docs/20 QA #1, #2): a second tap that lands on the next screen within this
    // window, e.g. on its route card or glasses button, is ignored.
    private var screenChangedAt = 0L
    private fun settled(): Boolean = android.os.SystemClock.uptimeMillis() - screenChangedAt > TAP_GUARD_MS
    private fun go(target: Screen) {
        if (_screen.value != target) screenChangedAt = android.os.SystemClock.uptimeMillis()
        _screen.value = target
    }

    private val selection = MutableStateFlow(Selection())

    // The search box text as Compose state, set synchronously on every key. `trip` is computed on Dispatchers.Default,
    // so feeding the TextField from trip.query let a late recomposition put back an older value and drop typed
    // characters ("ROOM" became "R" on the S25, docs/22 #2). Every selection change goes through updateSelection.
    var searchText by androidx.compose.runtime.mutableStateOf("")
        private set

    private fun updateSelection(change: (Selection) -> Selection) {
        selection.update(change)
        searchText = selection.value.query
    }

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _debugVisible = MutableStateFlow(false)
    val debugVisible: StateFlow<Boolean> = _debugVisible.asStateFlow()

    val arOverride = MutableStateFlow(ArOverride.AUTO)

    private val _guidance = MutableStateFlow<GuidanceController?>(null)
    val guidance: StateFlow<GuidanceController?> = _guidance.asStateFlow()

    val settings: StateFlow<AppSettings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val ttsStatus: StateFlow<TtsStatus> get() = app.speaker.status
    val clockMode: StateFlow<ClockMode> = app.clock.mode

    // Recents follow the selected building.
    private val recents = settings.flatMapLatest { app.settings.recents(it.buildingId) }

    // All of S1 and S1b, recomputed whenever any input changes (building, room, start,
    // avoid stairs, simulated time, approved shortcuts).
    val trip: StateFlow<TripUiState> = combine(
        settings,
        selection,
        recents,
        app.clock.mode,
        app.shortcuts.approvedEdges,
    ) { settings, sel, recentIds, _, approved ->
        val building = ExploreStart.building(app.building(settings.buildingId), sel.gps)
        val start = sel.startId?.let { building.nodes[it] } ?: building.node(building.defaultStartId)
        val destination = sel.destinationId?.let { building.nodes[it] }
        val plan = destination?.let {
            app.router.plan(
                building = building,
                startId = start.id,
                destinationId = it.id,
                time = app.clock.now(),
                avoidStairs = settings.avoidStairs,
                extraEdges = approved[building.id].orEmpty(),
            )
        }
        // Demo mode shows demo destinations only; otherwise recents first, then demo ones.
        val rowIds = if (settings.demoMode) building.demoDestinationIds
        else (recentIds + building.demoDestinationIds).distinct()
        TripUiState(
            settings = settings,
            building = building,
            destination = destination,
            start = start,
            startOptions = building.startIds.map { building.node(it) },
            query = sel.query,
            searchHits = search(building, sel.query),
            destinationRows = rowIds.mapNotNull { building.nodes[it] }.filter { it.kind == NodeKind.ROOM },
            plan = plan,
            simulatedTimeLabel = if (app.clock.isSimulated) Formats.dayTime(app.clock.now()) else null,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        app.building(settings.value.buildingId).let {
            TripUiState(building = it, start = it.node(it.defaultStartId), startOptions = it.startIds.map(it::node))
        },
    )

    val simulatedTimeLabel: StateFlow<String?> = app.clock.mode
        .map { if (it is ClockMode.Simulated) app.clock.label() else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { app.shortcuts.sync() }
    }

    // ---------- S1 ----------

    fun selectBuilding(id: String) {
        viewModelScope.launch { app.settings.setBuilding(id) }
        updateSelection { Selection() } // Different building file: clear the room and start
    }

    fun selectDestination(id: String) = updateSelection { it.copy(destinationId = id, query = "") }

    fun setQuery(text: String) = updateSelection { it.copy(query = text) }

    // Keyboard search key picks the top hit.
    fun submitSearch() {
        search(trip.value.building, searchText).firstOrNull()?.let { selectDestination(it.id) }
    }

    fun selectStart(id: String) = updateSelection { it.copy(startId = id) }

    fun setAvoidStairs(on: Boolean) {
        viewModelScope.launch { app.settings.setAvoidStairs(on) }
    }

    fun openRoutes() {
        if (!settled()) return
        val t = trip.value
        val destination = t.destination ?: return
        viewModelScope.launch { app.settings.addRecent(t.building.id, destination.id) }
        go(Screen.ROUTES)
    }

    fun openAddShortcut() {
        go(Screen.ADD_SHORTCUT)
    }

    // ---------- S1b ----------

    fun back() {
        when (_screen.value) {
            Screen.ROUTES, Screen.ADD_SHORTCUT -> go(Screen.DESTINATION)
            Screen.GUIDANCE, Screen.GLASSES -> endGuidance()
            Screen.DESTINATION -> if (_exploreHome.value) go(Screen.EXPLORE)
            Screen.EXPLORE -> Unit
        }
    }

    fun startGuidance(option: RouteOption) { if (settled()) startSession(option.route, glasses = false) }

    fun startAlreadyHere(route: Route) { if (settled()) startSession(route, glasses = false) }

    // "Guide me with glasses": follows the best route in S3.
    fun startGlasses() {
        if (!settled()) return
        val options = (trip.value.plan as? RoutePlan.Options)?.options ?: return
        startSession(options.first().route, glasses = true)
    }

    private fun startSession(route: Route, glasses: Boolean) {
        _guidance.value?.stop()
        val t = trip.value
        val controller = GuidanceController(
            scope = viewModelScope,
            building = t.building,
            firstRoute = route,
            lockedNotice = (t.plan as? RoutePlan.Options)?.lockedNotice,
            router = app.router,
            now = app.clock::now,
            avoidStairs = { settings.value.avoidStairs },
            extraEdges = { app.shortcuts.approvedEdges.value[t.building.id].orEmpty() },
            speaker = app.speaker,
            speakEnabled = { settings.value.speakInstructions },
            watch = app.watch,
        )
        controller.glassesMode = glasses
        controller.simulation.lowConfidence = _walk.value.lowConfidence
        _walk.update { it.copy(paused = false) }
        controller.start()
        _guidance.value = controller
        watchEnd.routeStarted()
        // Outdoor leg (LEG 2): the entrance the route walks to and the FusedLocation distance to it (geo/HandoffController).
        handoff.newRoute(com.campusmaps.geo.GeoEntrances.forRoute(app.appContext, route))
        runCatching { handoff.attach(viewModelScope, com.campusmaps.geo.FusedLocationFixes.flow(app.appContext)) }
            .onFailure { android.util.Log.w("Geo", "no location fixes for the hand-off", it) }
        if (glasses) {
            // Demo C: the first thing the glasses say is core's locked-entrance notice, then the instruction.
            val notice = (t.plan as? RoutePlan.Options)?.lockedNotice
            var noticeSpoken = notice == null
            var arrivalSpoken = false
            app.glasses.start(
                currentInstruction = {
                    val s = controller.state.value
                    if (s.arrived) arrivalSpoken = true
                    val text = if (s.arrived) s.step.text else s.bannerText
                    if (!noticeSpoken) { noticeSpoken = true; "Heads up: ${notice!!.text} $text" } else text
                },
                currentSign = { controller.state.value.let { s -> s.route.points.getOrNull(s.step.startIndex)?.node?.signText } },
                finished = { arrivalSpoken && controller.state.value.arrived },
            )
        }
        go(if (glasses) Screen.GLASSES else Screen.GUIDANCE)
    }

    // ---------- Explore (leg 1, outdoor map; outdoor/ExploreViewModel.kt) ----------

    // True once Explore is home (picked at start when far from every building) or was opened; Back on S1 then returns to it.
    private val _exploreHome = MutableStateFlow(false)
    val exploreHome: StateFlow<Boolean> = _exploreHome.asStateFlow()

    // LEG 2 hand-off (geo/): the 40 m trigger, the "Almost there" card and the map-to-AR transition state. One per app.
    val handoff = com.campusmaps.geo.HandoffController()

    // The map route (building/room/entrance) the hand-off was last armed for; the map re-arms only when it changes.
    var handoffRouteKey: String? = null

    // True while the current S2 session was started from the Explore map: S2 is then drawn inside the map-to-AR host
    // (CampusMapsApp) and End route returns to the map instead of S1b.
    private val _fromExplore = MutableStateFlow(false)
    val fromExplore: StateFlow<Boolean> = _fromExplore.asStateFlow()

    fun showExploreAsHome() {
        _exploreHome.value = true
        if (_screen.value == Screen.DESTINATION && selection.value.destinationId == null) go(Screen.EXPLORE)
    }

    fun openExplore() {
        _exploreHome.value = true
        go(Screen.EXPLORE)
    }

    // Search field on the Explore top bar.
    fun openSearchFromExplore() = go(Screen.DESTINATION)

    // "Start AR navigation" on Explore: same building, room and entrance as the map, started from a "Your location" node at
    // the real fix (ui/ExploreStart.kt; falls back to the nearest fixed start point without a fix), then the same S2 path S1b uses.
    // [fromCard]: the "Almost there" card (tap or its own timer) is not a stray tap, so the tap guard does not apply.
    fun startFromExplore(buildingId: String, destinationId: String, entranceId: String, lat: Double?, lng: Double?, fromCard: Boolean = false) {
        if (!fromCard && !settled()) return
        if (startingFromExplore || _guidance.value != null) return
        startingFromExplore = true
        viewModelScope.launch {
          try {
            if (settings.value.buildingId != buildingId) app.settings.setBuilding(buildingId)
            val building = app.building(buildingId)
            val (startId, gps) = ExploreStart.start(building, lat, lng) // "Your location" at the fix (ui/ExploreStart.kt)
            updateSelection { Selection(destinationId = destinationId, startId = startId, gps = gps) }
            val t = kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                trip.first { it.building.id == buildingId && it.destination?.id == destinationId && it.start.id == startId &&
                    (gps == null || it.building.outdoorStarts[startId] == gps) && it.plan != null }
            } ?: return@launch
            when (val plan = t.plan) {
                is RoutePlan.Options -> {
                    _fromExplore.value = true
                    val option = com.campusmaps.route.CoreRouter.optionForEntrance(t.building, plan.options, entranceId)
                    android.util.Log.i("Explore", "S2 from ${t.start.id}: map entrance $entranceId, S2 entrance ${option.entrance?.id}")
                    startSession(option.route, glasses = false)
                    handoff.startHandoff() // plays the 900 ms map-to-AR transition (no jump cut)
                }
                is RoutePlan.AlreadyHere -> startSession(plan.route, glasses = false)
                else -> go(Screen.ROUTES)
            }
          } finally {
            startingFromExplore = false
            // No session came of it (timeout or no plan): drop the card so the map is usable again.
            if (_guidance.value == null) handoff.backToMap()
          }
        }
    }
    private var startingFromExplore = false

    // ---------- S2 / S3 ----------

    // "End route", "Stop" and "Back to routes" all land on S1b.
    fun endGuidance() {
        // The student may have moved: S1b cards are recomputed from where they are now (Raphael's docs/20 behaviour).
        _guidance.value?.state?.value?.let { g ->
            val here = if (g.arrived) g.destination else g.route.points.getOrNull(g.progress.segmentIndex)?.node
            if (here != null && here.id != trip.value.start.id) updateSelection { it.copy(startId = here.id) }
        }
        val toMap = _fromExplore.value
        stopSession()
        go(if (toMap) Screen.EXPLORE else Screen.ROUTES)
    }

    // "Done" on the arrived banner: reset for the next judge.
    fun done() {
        stopSession()
        reset()
    }

    private val watchEnd = com.campusmaps.platform.WatchClearOnce { app.watch.clear() }

    private fun stopSession() {
        stopWalk()
        _guidance.value?.stop()
        _guidance.value = null
        app.glasses.stop()
        // Not in startSession(): a clear there would race the new route's first step. Once per route (done() calls this twice).
        watchEnd.routeEnded()
        handoff.detach()
        handoff.backToMap()
        _fromExplore.value = false
    }

    // ---------- App bar ----------

    // Reset: back to a clean S1 (keeps settings).
    fun reset() {
        stopSession()
        handoffRouteKey = null
        updateSelection { Selection() }
        go(Screen.DESTINATION)
    }

    // Settings "Reset demo": also restores demo defaults.
    fun resetDemo() {
        reset()
        app.clock.reset()
        arOverride.value = ArOverride.AUTO
        debugSetLowConfidence(false)
        viewModelScope.launch {
            app.settings.setAvoidStairs(false)
            app.settings.clearRecents(trip.value.building.id)
        }
        _showSettings.value = false
    }

    fun openSettings() {
        _showSettings.value = true
    }

    fun closeSettings() {
        _showSettings.value = false
    }

    // Long press on the title. Disabled in demo mode.
    fun toggleDebug() {
        if (settings.value.demoMode) return
        _debugVisible.update { !it }
    }

    fun hideDebug() {
        _debugVisible.value = false
    }

    // ---------- Settings sheet ----------

    fun setSpeak(on: Boolean) {
        viewModelScope.launch { app.settings.setSpeak(on) }
        if (!on) app.speaker.stop()
    }

    fun setDemoMode(on: Boolean) {
        viewModelScope.launch { app.settings.setDemoMode(on) }
        if (on) {
            _debugVisible.value = false
            updateSelection { it.copy(query = "") }
            if (_screen.value == Screen.ADD_SHORTCUT) go(Screen.DESTINATION)
        }
    }

    // ---------- Debug overlay ----------

    fun debugCycleClock() = app.clock.cycle()

    // Simulated-time switch (Raphael's debug overlay): on = Sat 26 Sep 2026 21:00, off = real clock.
    fun debugSetSimulated(on: Boolean) = app.clock.set(if (on) AppClock.DEFAULT_SIMULATED else ClockMode.Real)

    // Day chip + Material 3 TimePicker result.
    fun debugSetTime(day: DayOfWeek, time: LocalTime) = app.clock.set(ClockMode.Simulated(day, time))

    // ---- Fake walk (Raphael's Step / Walk / interval), on top of the teammate's continuous simulated walker ----

    private val _walk = MutableStateFlow(WalkState())
    val walk: StateFlow<WalkState> = _walk.asStateFlow()
    private var walkJob: Job? = null

    fun debugStep() {
        val g = _guidance.value ?: return
        g.simulation.paused = true
        _walk.update { it.copy(paused = true) }
        g.stepToNextNode()
    }

    // Walk: one node every N seconds until arrival. Pause stops it (the continuous walker stays paused).
    fun debugToggleWalk() {
        if (_walk.value.auto) {
            stopWalk()
            return
        }
        val g = _guidance.value ?: return
        g.simulation.paused = true
        _walk.update { it.copy(auto = true, paused = true) }
        walkJob = viewModelScope.launch {
            while (isActive && _guidance.value === g && !g.state.value.arrived) {
                delay(_walk.value.intervalSec * 1000L)
                g.stepToNextNode()
            }
            _walk.update { it.copy(auto = false) }
        }
    }

    private fun stopWalk() {
        walkJob?.cancel()
        walkJob = null
        _walk.update { it.copy(auto = false) }
    }

    fun debugWalkInterval(delta: Int) = _walk.update { it.copy(intervalSec = (it.intervalSec + delta).coerceIn(1, 20)) }

    // Continuous walker on/off (the teammate's simulated student).
    fun debugTogglePause() {
        val sim = _guidance.value?.simulation ?: return
        sim.paused = !sim.paused
        _walk.update { it.copy(paused = sim.paused) }
    }

    fun debugSetLowConfidence(on: Boolean) {
        _walk.update { it.copy(lowConfidence = on) }
        _guidance.value?.simulation?.lowConfidence = on
    }

    // Jump to node / start point: before guidance it sets "Where are you?"; during guidance it moves the student
    // (along the route, or off it with a reroute, including to an outdoor start point).
    fun debugJumpTo(nodeId: String) {
        val g = _guidance.value
        if (g == null) selectStart(nodeId) else g.jumpTo(nodeId)
    }

    fun debugCycleAr() {
        arOverride.update { ArOverride.entries[(it.ordinal + 1) % ArOverride.entries.size] }
    }

    fun debugDropConfidence() = _guidance.value?.simulation?.dropConfidence()

    fun debugPushOffRoute() = _guidance.value?.simulation?.pushOffRoute()

    fun debugSkipStep() = _guidance.value?.skipStep()

    fun debugToggleSpeed() {
        val sim = _guidance.value?.simulation ?: return
        sim.speedMultiplier = if (sim.speedMultiplier > 1.0) 1.0 else 4.0
    }

    fun debugSpeed(): Double = _guidance.value?.simulation?.speedMultiplier ?: 1.0

    fun debugToggleGlasses() = app.glasses.connectedFlag.update { !it }

    fun debugToggleOffline() = app.network.forceOffline.update { !it }

    val offline: StateFlow<Boolean> = app.network.online.map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun debugReview(approve: Boolean) {
        viewModelScope.launch {
            app.shortcutBackend.reviewAll(app.submitterIdProvider.currentId(), approve)
            app.shortcuts.sync()
        }
    }

    fun repeatInstruction() = _guidance.value?.repeat()

    // Search on S1: room number or name, prefix matches first, at most 5 hits.
    private fun search(building: Building, query: String): List<GraphNode> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return building.rooms
            .filter { room -> room.name.lowercase().contains(q) || room.name.filter(Char::isDigit).startsWith(q) }
            .sortedWith(compareBy({ !it.name.lowercase().removePrefix("room ").startsWith(q) }, { it.name }))
            .take(5)
    }

    companion object {
        private const val TAP_GUARD_MS = 600L
    }

    class Factory(private val app: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
    }
}
