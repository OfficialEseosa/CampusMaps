package com.campusmaps.ui

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
import com.campusmaps.routing.Formats
import com.campusmaps.routing.Route
import com.campusmaps.routing.RouteOption
import com.campusmaps.routing.RoutePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The screens from the flow in section 3 of the handoff.
enum class Screen { DESTINATION, ROUTES, GUIDANCE, GLASSES, ADD_SHORTCUT }

// What the user has picked on S1. Kept apart from settings because Reset clears it.
data class Selection(
    val destinationId: String? = null,
    val startId: String? = null, // Null = the building's default start
    val query: String = "",
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

    private val selection = MutableStateFlow(Selection())

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
        val building = app.building(settings.buildingId)
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
        selection.value = Selection() // Different building file: clear the room and start
    }

    fun selectDestination(id: String) = selection.update { it.copy(destinationId = id, query = "") }

    fun setQuery(text: String) = selection.update { it.copy(query = text) }

    // Keyboard search key picks the top hit.
    fun submitSearch() {
        trip.value.searchHits.firstOrNull()?.let { selectDestination(it.id) }
    }

    fun selectStart(id: String) = selection.update { it.copy(startId = id) }

    fun setAvoidStairs(on: Boolean) {
        viewModelScope.launch { app.settings.setAvoidStairs(on) }
    }

    fun openRoutes() {
        val t = trip.value
        val destination = t.destination ?: return
        viewModelScope.launch { app.settings.addRecent(t.building.id, destination.id) }
        _screen.value = Screen.ROUTES
    }

    fun openAddShortcut() {
        _screen.value = Screen.ADD_SHORTCUT
    }

    // ---------- S1b ----------

    fun back() {
        when (_screen.value) {
            Screen.ROUTES, Screen.ADD_SHORTCUT -> _screen.value = Screen.DESTINATION
            Screen.GUIDANCE, Screen.GLASSES -> endGuidance()
            Screen.DESTINATION -> Unit
        }
    }

    fun startGuidance(option: RouteOption) = startSession(option.route, glasses = false)

    fun startAlreadyHere(route: Route) = startSession(route, glasses = false)

    // "Guide me with glasses": follows the best route in S3.
    fun startGlasses() {
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
        controller.start()
        _guidance.value = controller
        if (glasses) {
            app.glasses.start(
                currentInstruction = { controller.state.value.let { s -> if (s.arrived) s.step.text else s.bannerText } },
                currentSign = { controller.state.value.let { s -> s.route.points.getOrNull(s.step.startIndex)?.node?.signText } },
            )
        }
        _screen.value = if (glasses) Screen.GLASSES else Screen.GUIDANCE
    }

    // ---------- S2 / S3 ----------

    // "End route", "Stop" and "Back to routes" all land on S1b.
    fun endGuidance() {
        stopSession()
        _screen.value = Screen.ROUTES
    }

    // "Done" on the arrived banner: reset for the next judge.
    fun done() {
        stopSession()
        reset()
    }

    private fun stopSession() {
        _guidance.value?.stop()
        _guidance.value = null
        app.glasses.stop()
        // Not in startSession(): a clear there would race the new route's first step.
        app.watch.clear()
    }

    // ---------- App bar ----------

    // Reset: back to a clean S1 (keeps settings).
    fun reset() {
        stopSession()
        selection.value = Selection()
        _screen.value = Screen.DESTINATION
    }

    // Settings "Reset demo": also restores demo defaults.
    fun resetDemo() {
        reset()
        app.clock.reset()
        arOverride.value = ArOverride.AUTO
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
            selection.update { it.copy(query = "") }
            if (_screen.value == Screen.ADD_SHORTCUT) _screen.value = Screen.DESTINATION
        }
    }

    // ---------- Debug overlay ----------

    fun debugCycleClock() = app.clock.cycle()

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

    class Factory(private val app: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
    }
}
