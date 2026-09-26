package com.campusmaps.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.campusmaps.AppContainer
import com.campusmaps.guidance.GuidanceState
import com.campusmaps.route.Formats
import com.campusmaps.outdoor.ExploreViewModel
import com.campusmaps.ui.screens.AddShortcutScreen
import com.campusmaps.ui.screens.ExploreActions
import com.campusmaps.ui.screens.ExploreScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusmaps.ui.screens.DebugLink
import com.campusmaps.ui.screens.DebugOverlay
import com.campusmaps.ui.screens.DebugControls
import com.campusmaps.ui.screens.rememberPressure
import com.campusmaps.ui.screens.DestinationActions
import com.campusmaps.ui.screens.DestinationScreen
import com.campusmaps.ui.screens.GlassesScreen
import com.campusmaps.ui.screens.GlassesUi
import com.campusmaps.ui.screens.GuidanceScreen
import com.campusmaps.ui.screens.RouteOptionsActions
import com.campusmaps.ui.screens.RouteOptionsScreen
import com.campusmaps.ui.screens.SettingsActions
import com.campusmaps.ui.screens.SettingsSheet
import com.campusmaps.ui.theme.CampusMapsTheme
import kotlinx.coroutines.flow.MutableStateFlow

// The whole app UI. Picks the screen, applies the theme rules from section 3:
// S1, S1b, S4 and Settings follow the system theme; S2, S3 and the debug card are always dark.
// In demo mode S1b is forced dark so the jump into S2 is not harsh on video.
@Composable
fun CampusMapsApp(app: AppContainer, vm: MainViewModel, shortcutVm: ShortcutViewModel) {
    val screen by vm.screen.collectAsState()
    val trip by vm.trip.collectAsState()
    val settings by vm.settings.collectAsState()
    val showSettings by vm.showSettings.collectAsState()
    val debugVisible by vm.debugVisible.collectAsState()
    val arOverride by vm.arOverride.collectAsState()
    val controller by vm.guidance.collectAsState()
    val guidance: GuidanceState? by (controller?.state ?: NoGuidance).collectAsState()
    val tts by vm.ttsStatus.collectAsState()
    val offline by vm.offline.collectAsState()
    val glassesConnected by app.glasses.connected.collectAsState()
    val glassesPhase by app.glasses.phase.collectAsState()
    val glassesSeen by app.glasses.seen.collectAsState()
    val glassesStill by app.glasses.lastStill.collectAsState()
    // Explore map (leg 1): its own state holder; home when far from every building (outdoor/ExploreViewModel.kt).
    val exploreHome by vm.exploreHome.collectAsState()
    val exploreVm: ExploreViewModel = viewModel(factory = ExploreViewModel.Factory(app, LocalContext.current))
    LaunchedEffect(Unit) { if (exploreVm.exploreShouldBeHome()) vm.showExploreAsHome() }
    // LEG 2: the 40 m trigger, the "Almost there" card and the 900 ms map-to-AR transition (geo/, ui/transition/).
    val handoffUi by vm.handoff.ui.collectAsState()
    val fromExplore by vm.fromExplore.collectAsState()
    val appContext = LocalContext.current.applicationContext
    val geo = androidx.compose.runtime.remember { com.campusmaps.geo.ArCoreGeospatialProvider(appContext) }
    // S2 started from the map is drawn inside the map-to-AR host, so the Crossfade must not switch screens for it.
    val crossTarget = if (screen == Screen.GUIDANCE && fromExplore) Screen.EXPLORE else screen

    val systemDark = isSystemInDarkTheme()
    val alwaysDark = screen == Screen.GUIDANCE || screen == Screen.GLASSES
    val screenDark = when (screen) {
        Screen.GUIDANCE, Screen.GLASSES -> true
        Screen.ROUTES -> systemDark || settings.demoMode
        Screen.EXPLORE -> false
        else -> systemDark
    }

    // Light status bar icons on dark screens, dark icons on light ones.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !screenDark
            isAppearanceLightNavigationBars = !screenDark
        }
    }

    // Demo mode keeps the screen on.
    DisposableEffect(settings.demoMode) {
        val window = (view.context as? Activity)?.window
        if (settings.demoMode) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }

    BackHandler(enabled = screen != Screen.EXPLORE && (screen != Screen.DESTINATION || exploreHome)) { vm.back() }

    CampusMapsTheme(darkTheme = screenDark) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(targetState = crossTarget, animationSpec = tween(250), label = "screen") { target ->
                when (target) {
                    Screen.DESTINATION -> DestinationScreen(
                        state = trip.copy(query = vm.searchText), // Synchronous search text (docs/22 #2)
                        buildings = app.buildings,
                        actions = DestinationActions(
                            onBuilding = vm::selectBuilding,
                            onQuery = vm::setQuery,
                            onSearch = vm::submitSearch,
                            onDestination = vm::selectDestination,
                            onStart = vm::selectStart,
                            onAvoidStairs = vm::setAvoidStairs,
                            onAddShortcut = vm::openAddShortcut,
                            onRoute = vm::openRoutes,
                            onReset = vm::reset,
                            onSettings = vm::openSettings,
                            onTitleLongPress = vm::toggleDebug,
                            onExplore = vm::openExplore,
                        ),
                    )
                    Screen.ROUTES -> RouteOptionsScreen(
                        state = trip,
                        actions = RouteOptionsActions(
                            onBack = vm::back,
                            onReset = vm::reset,
                            onSettings = vm::openSettings,
                            onAvoidStairs = vm::setAvoidStairs,
                            onPickRoute = vm::startGuidance,
                            onAlreadyHere = vm::startAlreadyHere,
                            onGlasses = vm::startGlasses,
                            onTitleLongPress = vm::toggleDebug,
                        ),
                    )
                    Screen.GUIDANCE -> guidance?.let {
                        val t by (controller?.buildingToWorld ?: NoTransform).collectAsState()
                        GuidanceScreen(
                            state = it, arOverride = arOverride, onEndRoute = vm::endGuidance, onDone = vm::done,
                            buildingToWorld = t, onBuildingToWorld = { v -> controller?.buildingToWorld?.value = v },
                            geo = geo, outdoorEntrance = handoffUi.entrance, outdoorDistanceM = handoffUi.distanceToEntranceM,
                        )
                    }
                    Screen.GLASSES -> guidance?.let {
                        GlassesScreen(
                            state = it,
                            glasses = GlassesUi(glassesConnected, glassesPhase, glassesSeen, glassesStill),
                            showFakeStep = !settings.demoMode,
                            onRepeat = vm::repeatInstruction,
                            onStop = vm::endGuidance,
                            onFakeStep = vm::debugSkipStep,
                            onDone = vm::done,
                            onBackToRoutes = vm::endGuidance,
                        )
                    }
                    Screen.ADD_SHORTCUT -> AddShortcutScreen(shortcutVm, onBack = vm::back)
                    Screen.EXPLORE -> {
                        // A room picked on S1 shows on the map too.
                        LaunchedEffect(trip.building.id, trip.destination?.id) {
                            trip.destination?.let { exploreVm.select(trip.building.id, it.id) }
                        }
                        val exploreState by exploreVm.state.collectAsState()
                        val plan = exploreState.plan
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        val startAr: (com.campusmaps.outdoor.EntrancePlan, Boolean) -> Unit = { p, fromCard ->
                            val f = exploreVm.state.value.fix
                            vm.startFromExplore(p.buildingId, p.destinationId, p.entranceId, f?.lat, f?.lng, fromCard)
                        }
                        // While only the map shows, the hand-off follows the map's recommended entrance so the 40 m
                        // trigger can raise the "Almost there" card before any S2 session exists.
                        LaunchedEffect(plan?.buildingId, plan?.destinationId, plan?.entranceId, guidance == null) {
                            if (guidance == null && plan != null) {
                                // Re-arm only for a new map route. After End route the trigger stays consumed (it
                                // re-arms above 60 m); re-arming here re-fired the card at once and reopened S2.
                                val key = "${plan.buildingId}/${plan.destinationId}/${plan.entranceId}"
                                if (vm.handoffRouteKey != key) {
                                    vm.handoffRouteKey = key
                                    vm.handoff.newRoute(com.campusmaps.geo.GeoEntrance(plan.entranceId, plan.entranceName,
                                        com.campusmaps.geo.LatLng(plan.entrance.lat, plan.entrance.lng)))
                                }
                                runCatching { vm.handoff.attach(scope, com.campusmaps.geo.FusedLocationFixes.flow(appContext)) }
                            }
                        }
                        // The card's "Go" starts S2 first; startFromExplore then plays the transition with S2 already
                        // composed underneath. (Animating before the session existed faded the map into an empty
                        // layer and then cut to S2.)
                        com.campusmaps.ui.transition.MapToArHost(
                            ui = handoffUi,
                            controller = vm.handoff,
                            onCardGo = { plan?.let { startAr(it, true) } },
                            mapContent = {
                                ExploreScreen(
                                    vm = exploreVm,
                                    actions = ExploreActions(
                                        onSearch = vm::openSearchFromExplore,
                                        onSettings = vm::openSettings,
                                        onStartAr = { startAr(it, false) },
                                    ),
                                )
                            },
                            arContent = {
                                val g = guidance
                                if (g != null && screen == Screen.GUIDANCE) {
                                    val t by (controller?.buildingToWorld ?: NoTransform).collectAsState()
                                    GuidanceScreen(
                                        state = g, arOverride = arOverride, onEndRoute = vm::endGuidance, onDone = vm::done,
                                        buildingToWorld = t, onBuildingToWorld = { v -> controller?.buildingToWorld?.value = v },
                                        geo = geo, outdoorEntrance = handoffUi.entrance, outdoorDistanceM = handoffUi.distanceToEntranceM,
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize())
                                }
                            },
                        )
                    }
                }
            }

            if (showSettings) {
                // The sheet follows the system theme even when the screen under it is forced dark.
                CampusMapsTheme(darkTheme = if (alwaysDark) true else systemDark) {
                    SettingsSheet(
                        settings = settings,
                        tts = tts,
                        buildings = app.buildings,
                        actions = SettingsActions(
                            onSpeak = vm::setSpeak,
                            onAvoidStairs = vm::setAvoidStairs,
                            onDemoMode = vm::setDemoMode,
                            onBuilding = vm::selectBuilding,
                            onResetDemo = vm::resetDemo,
                            onDismiss = vm::closeSettings,
                        ),
                    )
                }
            }

            if (debugVisible && !settings.demoMode) {
                val g = guidance
                val clockMode by vm.clockMode.collectAsState()
                val walk by vm.walk.collectAsState()
                val watchCount by app.watch.connectedCount.collectAsState()
                val pressure = rememberPressure()
                val arCore = rememberArCoreAvailability()
                val problems = app.loadProblems[trip.building.code].orEmpty()
                val lines = buildList {
                    add("screen: ${screen.name.lowercase()}  building: ${trip.building.code}")
                    add("clock: ${app.clock.label()}  now: ${Formats.dayTime(app.clock.now())}")
                    add("start: ${trip.start.id} (${if (trip.start.isOutdoor) "outside" else Formats.floorShort(trip.start.floor)})")
                    add("ar: ${arOverride.label}  ARCore: $arCore")
                    add("barometer: ${pressure?.let { "%.2f hPa".format(it) } ?: "no barometer"}")
                    add("watch: ${if (watchCount < 0) "not asked yet" else "$watchCount connected"}  glasses: ${if (glassesConnected) "connected (${app.glasses::class.simpleName})" else "not connected"}")
                    add("tts: ${tts.name.lowercase()}  network: ${if (offline) "offline" else "online"}")
                    add("validator: ${problems.size} problems (ERROR/WARN, see Logcat)")
                    if (g != null) {
                        val node = g.route.points.getOrNull(g.progress.segmentIndex)?.node
                        add("node: ${node?.id ?: "-"}  ${Formats.floorShort(g.floor)}  conf ${"%.2f".format(g.pose.confidence)}")
                        add("pose: x=${"%.1f".format(g.pose.position.x)} y=${"%.1f".format(g.pose.position.y)} heading ${Math.toDegrees(g.pose.headingRad).toInt()}")
                        add("step: ${g.progress.stepIndex + 1}/${g.route.steps.size} ${g.step.kind.name.lowercase()}")
                        add("along: ${"%.1f".format(g.progress.alongM)} m  off: ${"%.1f".format(g.progress.offRouteM.coerceAtMost(999.0))} m")
                        add("reroutes: ${g.rerouteCount}  speed: x${vm.debugSpeed().toInt()}")
                    }
                }
                val links = buildList {
                    add(DebugLink("Cycle simulated time (shortcut)", vm::debugCycleClock))
                    add(DebugLink("Open Explore map", vm::openExplore))
                    add(DebugLink("AR: ${arOverride.label} (tap to change)", vm::debugCycleAr))
                    if (g != null) {
                        add(DebugLink("Drop confidence for 4 s (Locate me)", vm::debugDropConfidence))
                        add(DebugLink("Force reroute", vm::debugPushOffRoute))
                        add(DebugLink("Skip step", vm::debugSkipStep))
                        add(DebugLink("Walk speed x1 / x4", vm::debugToggleSpeed))
                    }
                    add(DebugLink("Glasses: ${if (glassesConnected) "connected" else "unplugged"}", vm::debugToggleGlasses))
                    add(DebugLink("Offline: ${if (offline) "on" else "off"}", vm::debugToggleOffline))
                    add(DebugLink("Approve my pending shortcuts") { vm.debugReview(approve = true) })
                    add(DebugLink("Reject my pending shortcuts") { vm.debugReview(approve = false) })
                }
                val here = g?.route?.points?.getOrNull(g.progress.segmentIndex)?.node ?: trip.start
                DebugOverlay(guidance = g, lines = lines, links = links, onClose = vm::hideDebug) {
                    DebugControls(
                        clock = clockMode,
                        walk = walk,
                        guiding = g != null,
                        arrived = g?.arrived == true,
                        jumpTargets = trip.startOptions,
                        jumpLabel = if (here.isOutdoor) "outside: ${here.id}" else here.id,
                        onSimulated = vm::debugSetSimulated,
                        onSetTime = vm::debugSetTime,
                        onStep = vm::debugStep,
                        onWalk = vm::debugToggleWalk,
                        onInterval = vm::debugWalkInterval,
                        onPause = vm::debugTogglePause,
                        onLowConfidence = vm::debugSetLowConfidence,
                        onJump = vm::debugJumpTo,
                    )
                }
            }
        }
    }
}

// ARCore's own availability string for the debug card (Raphael's line), e.g. SUPPORTED_INSTALLED or UNKNOWN_ERROR.
@Composable
private fun rememberArCoreAvailability(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    var value by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("checking") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        repeat(10) {
            val a = try {
                com.google.ar.core.ArCoreApk.getInstance().checkAvailability(context)
            } catch (e: Exception) {
                value = "error: ${e.message}"
                return@LaunchedEffect
            }
            value = a.name
            if (!a.isTransient) return@LaunchedEffect
            kotlinx.coroutines.delay(300)
        }
    }
    return value
}

private val NoTransform = MutableStateFlow<com.campusmaps.loc.BuildingToWorld?>(null)

// Stand-in flow while there is no guidance session.
private val NoGuidance = MutableStateFlow<GuidanceState?>(null)
