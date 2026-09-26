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
import kotlinx.coroutines.flow.distinctUntilChanged
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

// The screens from the flow in section 3 of the handoff, plus the redesign's campus picker (CAMPUS),
// building picker (BUILDINGS) and 3D route preview (PREVIEW). EDITOR is the building editor (Settings, editor/).
enum class Screen { CAMPUS, BUILDINGS, DESTINATION, ROUTES, PREVIEW, GUIDANCE, GLASSES, ADD_SHORTCUT, EXPLORE, EDITOR }

// "Guide me with" on S1b. MAP is S2 with the camera forced off (text and the big map).
enum class GuideMode { PHONE, GLASSES, MAP }

// What the user has picked on S1. Kept apart from settings because Reset clears it.
data class Selection(
    val destinationId: String? = null,
    val startId: String? = null, // Null = the building's default start
    val query: String = "",
    // The phone's fix when the trip was started from the Explore map: adds the "Your location" start node (CoreBridge.withGpsStart).
    val gps: com.campusmaps.data.model.OutdoorStart? = null,
    // Set while the start is S1's automatic GPS default (ui/GpsStart): the fix's accuracy in metres. Null = picked by hand,
    // from Explore, or the building's default. Only an automatic start is replaced by a newer fix.
    val gpsAutoAccuracyM: Double? = null,
    // "Route me around" on the PantherCard card: the route (building|start|destination) it was pressed for.
    val routedAroundKey: String? = null,
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
    val gpsHint: String? = null, // "From your location (GPS, 8 m)" while the start is the automatic GPS default
    val cardPrompt: com.campusmaps.route.CardPrompt? = null, // S1b PantherCard card (route/CardAccess.kt)
    val cardHint: String? = null, // S1 one-line hint under the building pill
    val cardRoutedAround: Boolean = false, // "Route me around" pressed for this route: plan without the card
) {
    val startsInside: Boolean get() = !start.isOutdoor
    val routeError: String? get() = (plan as? RoutePlan.NoRoute)?.message
}

class MainViewModel(private val app: AppContainer) : ViewModel() {

    private val _screen = MutableStateFlow(Screen.CAMPUS)
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
        val routedAround = sel.routedAroundKey != null && sel.routedAroundKey == "${building.id}|${start.id}|${destination?.id}"
        val plan = destination?.let {
            app.router.plan(
                building = building,
                startId = start.id,
                destinationId = it.id,
                time = app.clock.now(),
                avoidStairs = settings.avoidStairs,
                extraEdges = approved[building.id].orEmpty(),
                hasCard = settings.hasCard && !routedAround,
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
            cardPrompt = com.campusmaps.route.CardAccess.prompt(plan, building, app.clock.now(), settings.hasCard, routedAround),
            cardHint = com.campusmaps.route.CardAccess.hint(building, app.clock.now(), settings.hasCard),
            cardRoutedAround = routedAround,
            gpsHint = sel.gpsAutoAccuracyM?.takeIf { start.id == com.campusmaps.data.campus.CoreBridge.GPS_START_ID }?.let(GpsStart::hint),
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

    // ---------- S0 campus, S0b buildings ----------

    // The campus in use: set by the S0 picker, otherwise the campus of the saved building.
    private val _campus = MutableStateFlow(com.campusmaps.data.campus.Campuses.of(settings.value.buildingId).id)
    val campus: StateFlow<com.campusmaps.data.campus.CampusId> = _campus.asStateFlow()

    init {
        // The campus skin follows the building in use: every building change (S0b, S1 chips, Settings, the Explore map,
        // and the saved building once it loads) sets the campus. The S0 card and the S0b swap set it on their own.
        viewModelScope.launch {
            app.settings.settings.map { it.buildingId }.distinctUntilChanged().collect { id ->
                _campus.value = com.campusmaps.data.campus.Campuses.of(id).id
            }
        }
    }

    fun pickCampus(id: com.campusmaps.data.campus.CampusId) {
        _campus.value = id
        go(Screen.BUILDINGS)
    }

    // The swap pill on S0b: the other campus, staying on S0b.
    fun swapCampus() {
        _campus.update { if (it == com.campusmaps.data.campus.CampusId.GT) com.campusmaps.data.campus.CampusId.GSU else com.campusmaps.data.campus.CampusId.GT }
    }

    fun openCampus() = go(Screen.CAMPUS)

    fun openBuildings() = go(Screen.BUILDINGS)

    // A mapped building on S0b: load it and go to "Where to?".
    fun openBuilding(id: String) {
        if (!settled()) return
        if (id != settings.value.buildingId) selectBuilding(id)
        go(Screen.DESTINATION)
    }

    // ---------- S1 ----------

    fun selectBuilding(id: String) {
        _campus.value = com.campusmaps.data.campus.Campuses.of(id).id
        viewModelScope.launch { app.settings.setBuilding(id) }
        updateSelection { Selection() } // Different building file: clear the room and start
    }

    fun selectDestination(id: String) = updateSelection { it.copy(destinationId = id, query = "") }

    fun setQuery(text: String) = updateSelection { it.copy(query = text) }

    // Keyboard search key picks the top hit.
    fun submitSearch() {
        search(trip.value.building, searchText).firstOrNull()?.let { selectDestination(it.id) }
    }

    fun selectStart(id: String) = updateSelection { it.copy(startId = id, gpsAutoAccuracyM = null) } // by hand: kept

    // S1 opened directly: while "Where to?" shows, the phone's fix (no permission prompt here; Explore asks) may set the
    // start to "Your location" (ui/GpsStart). Never over a start picked by hand; never in demo mode.
    private val s1Fix = MutableStateFlow<com.campusmaps.geo.LocationFix?>(null)

    init {
        viewModelScope.launch {
            _screen.map { it == Screen.DESTINATION }.distinctUntilChanged()
                .flatMapLatest { onS1 -> if (onS1) com.campusmaps.geo.FusedLocationFixes.flow(app.appContext) else kotlinx.coroutines.flow.emptyFlow() }
                .collect { s1Fix.value = it }
        }
        viewModelScope.launch {
            combine(s1Fix, settings, selection, _screen) { fix, st, sel, screen -> Triple(fix, st, sel) to screen }
                .collect { (inputs, screen) ->
                    val (fix, st, sel) = inputs
                    if (screen != Screen.DESTINATION || _guidance.value != null) return@collect
                    val next = GpsStart.apply(sel, app.building(st.buildingId), fix, System.currentTimeMillis(), st.demoMode)
                    if (next != sel) {
                        android.util.Log.i("GpsStart", if (next.gpsAutoAccuracyM != null)
                            "S1 start: Your location (%.6f, %.6f, %.0f m) for ${st.buildingId}".format(next.gps!!.lat, next.gps.lng, next.gpsAutoAccuracyM)
                        else "S1 start: ${st.buildingId} default (fix ${fix?.let { "%.6f, %.6f, %.0f m".format(it.lat, it.lng, it.accuracyM) } ?: "none"}, demo ${st.demoMode})")
                        updateSelection { cur -> if (cur == sel) next else cur }
                    }
                }
        }
    }

    // "Find me" on S1: the node a sign or room number was read at becomes the start. A room node carries its own
    // floor, so a room read on floor 6 starts the route on floor 6. Unknown ids are ignored.
    fun startFromSign(nodeId: String) {
        if (trip.value.building.nodes[nodeId] == null) return
        updateSelection { it.copy(startId = nodeId) }
    }

    // PantherCard: the Settings switch and the S1b card's "I have my card" (the plan recomputes from the setting).
    fun setHasCard(on: Boolean) {
        if (on) updateSelection { it.copy(routedAroundKey = null) }
        viewModelScope.launch { app.settings.setHasCard(on) }
    }

    // S1b card's "Route me around": plan this route without the card and hide the card for it.
    fun routeAroundCard() {
        val t = trip.value
        updateSelection { it.copy(routedAroundKey = "${t.building.id}|${t.start.id}|${t.destination?.id}") }
    }

    fun setAvoidStairs(on: Boolean) {
        viewModelScope.launch { app.settings.setAvoidStairs(on) }
    }

    fun openRoutes() {
        if (!settled()) return
        val t = trip.value
        val destination = t.destination ?: return
        viewModelScope.launch { app.settings.addRecent(t.building.id, destination.id) }
        _selectedRouteId.value = null
        go(Screen.ROUTES)
    }

    fun openAddShortcut() {
        go(Screen.ADD_SHORTCUT)
    }

    // ---------- S1b ----------

    fun back() {
        when (_screen.value) {
            Screen.CAMPUS -> Unit
            Screen.BUILDINGS -> go(Screen.CAMPUS)
            Screen.ROUTES, Screen.ADD_SHORTCUT -> go(Screen.DESTINATION)
            Screen.PREVIEW -> go(Screen.ROUTES)
            Screen.GUIDANCE, Screen.GLASSES -> endGuidance()
            Screen.DESTINATION -> if (_exploreHome.value) go(Screen.EXPLORE) else go(Screen.BUILDINGS)
            Screen.EXPLORE -> if (_exploreBackToS1.value) { _exploreBackToS1.value = false; go(Screen.DESTINATION) }
            Screen.EDITOR -> buildingEditor(false)
        }
    }

    // "Guide me with" (S1b mode tiles). Remembered for the session.
    private val _guideMode = MutableStateFlow(GuideMode.PHONE)
    val guideMode: StateFlow<GuideMode> = _guideMode.asStateFlow()
    fun setGuideMode(mode: GuideMode) { _guideMode.value = mode }

    // The route card picked on S1b; null = the fastest (first) option.
    private val _selectedRouteId = MutableStateFlow<String?>(null)
    val selectedRouteId: StateFlow<String?> = _selectedRouteId.asStateFlow()
    fun selectRoute(option: RouteOption) { _selectedRouteId.value = option.id }

    // The XR preview card on S1b and its "Start AR guidance" button.
    fun openPreview() { if (settled() && trip.value.plan is RoutePlan.Options) go(Screen.PREVIEW) }

    // The option S1b's Start button (and the 3D preview) follow: the picked card if it is still offered, else the fastest.
    fun selectedOption(): RouteOption? {
        val options = (trip.value.plan as? RoutePlan.Options)?.options ?: return null
        return options.firstOrNull { it.id == _selectedRouteId.value } ?: options.first()
    }

    // "Start with <mode>" on S1b.
    fun startSelected() {
        if (!settled()) return
        when (val plan = trip.value.plan) {
            is RoutePlan.AlreadyHere -> startSession(plan.route, glasses = false)
            is RoutePlan.Options -> {
                val option = selectedOption() ?: return
                startSession(option.route, glasses = _guideMode.value == GuideMode.GLASSES)
            }
            else -> Unit
        }
    }

    // "Start AR guidance" on the 3D preview: phone guidance (map mode stays map mode).
    fun startFromPreview() {
        if (!settled()) return
        if (_guideMode.value == GuideMode.GLASSES) _guideMode.value = GuideMode.PHONE
        selectedOption()?.let { startSession(it.route, glasses = false) }
    }

    fun startGuidance(option: RouteOption) { if (settled()) startSession(option.route, glasses = false) }

    fun startAlreadyHere(route: Route) { if (settled()) startSession(route, glasses = false) }

    // "Guide me with glasses": follows the best route in S3.
    fun startGlasses() {
        if (!settled()) return
        val options = (trip.value.plan as? RoutePlan.Options)?.options ?: return
        startSession(options.first().route, glasses = true)
    }

    private fun startSession(route: Route, glasses: Boolean, seedFix: com.campusmaps.geo.LocationFix? = null) {
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
            hasCard = { settings.value.hasCard && !t.cardRoutedAround },
        )
        controller.glassesMode = glasses
        controller.simulation.lowConfidence = _walk.value.lowConfidence
        _walk.update { it.copy(paused = false) }
        seedFix?.let(controller::onFix)
        controller.start()
        _guidance.value = controller
        // Explore start: the outdoor steps are followed by FusedLocation (guidance/OutdoorGps.kt).
        outdoorFixJob?.cancel()
        outdoorFixJob = if (route.steps.any { it.outdoorEnd != null }) viewModelScope.launch {
            runCatching { com.campusmaps.geo.VpsPosition.preferVps(com.campusmaps.geo.FusedLocationFixes.flow(app.appContext)).collect { controller.onFix(it) } }
                .onFailure { android.util.Log.w("Outdoor", "no location fixes for the street steps", it) }
        } else null
        watchEnd.routeStarted()
        // Outdoor leg (LEG 2): the entrance the route walks to and the FusedLocation distance to it (geo/HandoffController).
        handoff.newRoute(com.campusmaps.geo.GeoEntrances.forRoute(app.appContext, route))
        runCatching { handoff.attach(viewModelScope, com.campusmaps.geo.VpsPosition.preferVps(com.campusmaps.geo.FusedLocationFixes.flow(app.appContext))) }
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
        vpsSnapJob?.cancel()
        vpsSnapJob = if (route.startsOutside) viewModelScope.launch { vpsSnap(controller, t.building) } else null
        go(if (glasses) Screen.GLASSES else Screen.GUIDANCE)
    }

    // Entrance snap: a VPS position within 15 m of any outdoor entrance of this building means the student is at that door.
    // The outdoor leg ends there; a different door than planned reroutes from it (same floor-change method). Once per route.
    private var vpsSnapJob: Job? = null
    private suspend fun vpsSnap(controller: GuidanceController, building: Building) {
        val snap = com.campusmaps.geo.VpsPosition.fixes.map { fix ->
            val g = controller.state.value
            if (fix == null || _guidance.value !== controller || !g.startsOutside || g.arrived) null
            else com.campusmaps.geo.VpsPosition.snap(building.core, fix, g.route.points.firstOrNull { it.node.kind == NodeKind.ENTRANCE }?.node?.id)
        }.first { it != null }!!
        android.util.Log.i("Geo", com.campusmaps.geo.VpsPosition.logLine(snap))
        controller.jumpTo(snap.entranceId) // on the route: skip ahead to the door; another door: reroute from it
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
        if (_screen.value in setOf(Screen.CAMPUS, Screen.DESTINATION) && selection.value.destinationId == null) go(Screen.EXPLORE)
    }

    // Demo mode at start: S1 ("Where to?") is home, not the campus picker (docs/22 rule, kept through the redesign).
    fun showDemoHome() {
        if (_screen.value == Screen.CAMPUS) go(Screen.DESTINATION)
    }

    // Explore opened from S1 (the map row or the debug link) while S1 is home: Back returns to S1 and S1 stays home.
    private val _exploreBackToS1 = MutableStateFlow(false)
    val exploreBackToS1: StateFlow<Boolean> = _exploreBackToS1.asStateFlow()

    fun openExplore() {
        _exploreBackToS1.value = !_exploreHome.value
        go(Screen.EXPLORE)
    }

    // Search field on the Explore top bar.
    fun openSearchFromExplore() {
        _exploreBackToS1.value = false
        go(Screen.DESTINATION)
    }

    // "Start AR navigation" on Explore: same building, room and entrance as the map, started from a "Your location" node at
    // the real fix (ui/ExploreStart.kt; falls back to the nearest fixed start point without a fix), then the same S2 path S1b uses.
    // [fromCard]: the "Almost there" card (tap or its own timer) is not a stray tap, so the tap guard does not apply.
    // [streets]: Google's walking maneuvers from the map (empty without Directions); [entrance]: the map's entrance point,
    // null when it is only approximate. S2's outdoor leg then follows them by GPS (outdoor/StreetSteps.kt).
    fun startFromExplore(buildingId: String, destinationId: String, entranceId: String, lat: Double?, lng: Double?, fromCard: Boolean = false,
                         streets: List<com.campusmaps.outdoor.StreetStep> = emptyList(), entrance: com.campusmaps.outdoor.LatLngPoint? = null,
                         entranceName: String? = null) {
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
                    val sameDoor = option.entrance?.id == entranceId
                    val route = if (gps != null && entrance != null && sameDoor) com.campusmaps.outdoor.StreetSteps.apply(
                        option.route, streets, entrance, t.building.name, entranceName ?: option.entrance?.name.orEmpty(),
                    ) else option.route
                    android.util.Log.i("Outdoor", "S2 outdoor steps: ${route.steps.count { it.outdoorEnd != null }} by GPS " +
                        "(${streets.size} from Directions${if (!sameDoor) ", map door differs: none" else ""}): " +
                        route.steps.filter { it.outdoorEnd != null }.joinToString(" | ") { it.text })
                    // The map's fix (seconds old) until FusedLocation's first one, so the first banner is already a GPS distance.
                    startSession(route, glasses = false,
                        seedFix = gps?.let { com.campusmaps.geo.LocationFix(it.lat, it.lng, 10.0, System.currentTimeMillis()) })
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
    private var outdoorFixJob: kotlinx.coroutines.Job? = null

    // ---------- S2 / S3 ----------

    // "End route", "Stop" and "Back to routes" all land on S1b.
    fun endGuidance() {
        // The student may have moved: S1b cards are recomputed from where they are now (Raphael's docs/20 behaviour).
        _guidance.value?.state?.value?.let { g ->
            val here = if (g.arrived) g.destination else g.route.points.getOrNull(g.progress.segmentIndex)?.node
            if (here != null && here.id != trip.value.start.id) updateSelection { it.copy(startId = here.id, gpsAutoAccuracyM = null) }
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
        outdoorFixJob?.cancel()
        outdoorFixJob = null
        vpsSnapJob?.cancel()
        vpsSnapJob = null
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

    // Building editor (Settings > Edit this building, editor/). Closing clears the selection so S1 re-reads the reloaded building.
    fun buildingEditor(open: Boolean) {
        _showSettings.value = false
        if (open) go(Screen.EDITOR) else { updateSelection { Selection(query = " ") }; updateSelection { Selection() }; go(Screen.DESTINATION) }
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

    // Force reroute: push 8 m off the path, and once the controller has rerouted, put the student on the start of the
    // new route. Without the snap the student is still more than 6 m off the fresh route 3 s later (the lead-in walk
    // is slower than that), so the controller rerouted a second time and one tap counted 2 reroutes.
    fun debugPushOffRoute() {
        val c = _guidance.value ?: return
        val before = c.state.value.rerouteCount
        c.simulation.pushOffRoute()
        viewModelScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(5_000L) { c.state.first { it.rerouteCount > before } } ?: return@launch
            c.simulation.jumpToPoint(0)
        }
    }

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
