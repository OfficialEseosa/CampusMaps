package com.campusmaps.outdoor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campusmaps.AppContainer
import com.campusmaps.BuildConfig
import com.campusmaps.data.model.NodeKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// A room the student can pick on the Explore map (demo destinations of every building).
data class ExploreDestination(val buildingId: String, val buildingCode: String, val nodeId: String, val name: String) {
    val label: String get() = "$buildingCode ${name.removePrefix("Room ")}"
}

data class ExploreUiState(
    val hasPermission: Boolean = false,
    val fix: UserFix? = null,
    val destinations: List<ExploreDestination> = emptyList(),
    val selected: ExploreDestination? = null,
    val plan: EntrancePlan? = null,
    val route: OutdoorRoute? = null,
    val steps: List<String> = emptyList(),
    val satellite: Boolean = false,
    val noRoute: String? = null,
    val destinationKind: String = "Room",
)

// State holder for the Explore screen (leg 1): position, chosen room, recommended entrance, line and steps.
class ExploreViewModel(private val app: AppContainer, context: Context) : ViewModel() {

    val location = UserLocationProvider(context)
    private val directions = DirectionsClient(BuildConfig.MAPS_API_KEY)
    val hasMapsKey: Boolean get() = directions.enabled

    private val _state = MutableStateFlow(ExploreUiState(destinations = allDestinations()))
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    // Seam for the Geospatial agent: metres from the student to the recommended entrance, null without fix or plan.
    // Hand over to Geospatial / AR when this drops under about 40 m.
    @OptIn(ExperimentalCoroutinesApi::class)
    val distanceToEntranceM: StateFlow<Double?> = _state
        .map { it.plan?.entrance }
        .distinctUntilChanged()
        .flatMapLatest { p -> p?.let(location::distanceTo) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var computedFrom: LatLngPoint? = null
    private var directionsJob: Job? = null
    private val directionsCache = DirectionsCache()

    init {
        viewModelScope.launch {
            location.fix.collect { f ->
                _state.update { it.copy(fix = f) }
                val last = computedFrom
                if (f != null && (last == null || OutdoorRoutes.distanceM(last, f.point) > RECOMPUTE_M)) recompute()
            }
        }
    }

    // Called at start: true when Explore should be home (far from every building or no fix, not in demo mode).
    suspend fun exploreShouldBeHome(): Boolean {
        val demo = app.settings.settings.first().demoMode
        if (demo) return false
        val fix = location.lastKnown(3_000L)
        return OutdoorRoutes.exploreIsHome(app.buildings, fix?.point, demo)
    }

    fun onPermission(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted || location.hasPermission()) }
        if (location.hasPermission()) location.start()
    }

    fun onVisible() = onPermission(location.hasPermission())

    fun onHidden() = location.stop()

    fun toggleSatellite() = _state.update { it.copy(satellite = !it.satellite) }

    fun select(buildingId: String, nodeId: String) {
        val b = app.buildings.firstOrNull { it.id == buildingId } ?: return
        val n = b.nodes[nodeId] ?: return
        val d = ExploreDestination(b.id, b.code, n.id, n.name)
        if (_state.value.selected == d) return
        _state.update { it.copy(selected = d, plan = null, route = null, steps = emptyList(), noRoute = null,
            destinationKind = if (n.kind == NodeKind.ROOM) "Room" else n.kind.name.lowercase().replaceFirstChar(Char::uppercase)) }
        recompute()
    }

    private fun recompute() {
        val sel = _state.value.selected ?: return
        val building = app.buildings.firstOrNull { it.id == sel.buildingId } ?: return
        val fix = _state.value.fix
        // Without a fix, core still picks an entrance (from the building origin); no line is drawn.
        val from = fix?.point ?: LatLngPoint(building.core.origin.lat, building.core.origin.lng)
        computedFrom = fix?.point
        directionsJob?.cancel()
        directionsJob = viewModelScope.launch {
            val avoidStairs = app.settings.settings.first().avoidStairs
            val plan = withContext(Dispatchers.Default) {
                OutdoorRoutes.plan(building, sel.nodeId, from, app.clock.now(), avoidStairs)
            }
            if (plan == null) {
                _state.update { it.copy(plan = null, route = null, steps = emptyList(), noRoute = "No way in to ${sel.name} right now") }
                return@launch
            }
            val straight = fix?.let { OutdoorRoutes.straight(it.point, plan.entrance) }
            _state.update { it.copy(plan = plan, route = straight, steps = OutdoorRoutes.sheetSteps(straight, plan), noRoute = null) }
            if (fix == null || !directions.enabled || !app.network.online.value) return@launch
            val key = "${plan.buildingId}/${plan.entranceId}" // entrance ids like E-N repeat across buildings
            val walked = if (directionsCache.shouldRequest(key, fix.point)) {
                android.util.Log.i("Directions", "request for $key")
                directions.walking(fix.point, plan.entrance).also { directionsCache.store(key, fix.point, it) }
            } else directionsCache.cached()
            walked ?: return@launch
            _state.update { if (it.plan == plan) it.copy(route = walked, steps = OutdoorRoutes.sheetSteps(walked, plan)) else it }
        }
    }

    private fun allDestinations(): List<ExploreDestination> = app.buildings.flatMap { b ->
        b.demoDestinationIds.mapNotNull { id -> b.nodes[id]?.let { ExploreDestination(b.id, b.code, id, it.name) } }
    }

    override fun onCleared() {
        location.stop()
    }

    companion object {
        // Recompute the entrance and line after the student moves this far.
        private const val RECOMPUTE_M = 15.0
        // Campus centre used when there is no location permission (Georgia State downtown).
        val CAMPUS_CENTER = LatLngPoint(33.7530, -84.3853)
    }

    class Factory(private val app: AppContainer, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExploreViewModel(app, context.applicationContext) as T
    }
}
