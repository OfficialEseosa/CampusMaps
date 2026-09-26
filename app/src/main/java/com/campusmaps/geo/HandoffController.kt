package com.campusmaps.geo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the map and S2 need from the hand-off. */
data class HandoffUi(
    val phase: HandoffPhase = HandoffPhase.MAP,
    /** FusedLocation distance to the chosen entrance, metres; null until a fresh fix (the banner then shows the step text only). */
    val distanceToEntranceM: Double? = null,
    val entrance: GeoEntrance? = null,
)

/**
 * Holds one route's hand-off: feeds location fixes into [HandoffStateMachine] and exposes [ui].
 * The map agent's "AR" button calls [startHandoff]; the transition calls [animationDone] when its 900 ms run ends.
 * [nowMs] is injected (wall clock in the app, fixed in tests), so staleness is testable.
 */
class HandoffController(private val nowMs: () -> Long = System::currentTimeMillis) {
    private val machine = HandoffStateMachine()
    private val _ui = MutableStateFlow(HandoffUi())
    val ui: StateFlow<HandoffUi> = _ui.asStateFlow()
    private var job: Job? = null

    /** A new route started. [entrance] null (no lat/lng in the building file) means no automatic trigger; the button still works. */
    fun newRoute(entrance: GeoEntrance?) {
        machine.newRoute(entrance?.latLng)
        _ui.value = HandoffUi(entrance = entrance)
    }

    /** Start listening to [fixes] (cancels the previous listener). */
    fun attach(scope: CoroutineScope, fixes: Flow<LocationFix>) {
        job?.cancel()
        job = scope.launch { fixes.collect { onFix(it) } }
    }

    fun detach() { job?.cancel(); job = null }

    fun onFix(fix: LocationFix) {
        val phase = machine.onFix(fix, nowMs())
        _ui.value = _ui.value.copy(phase = phase, distanceToEntranceM = machine.trigger.lastDistanceM ?: _ui.value.distanceToEntranceM)
    }

    /** Manual "AR" button (map agent) and the card's advance. */
    fun startHandoff() { _ui.value = _ui.value.copy(phase = machine.startHandoff()) }

    fun animationDone() { _ui.value = _ui.value.copy(phase = machine.animationDone()) }

    fun backToMap() { _ui.value = _ui.value.copy(phase = machine.backToMap()) }
}
