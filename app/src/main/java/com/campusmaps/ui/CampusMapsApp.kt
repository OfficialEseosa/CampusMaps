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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.campusmaps.AppContainer
import com.campusmaps.guidance.GuidanceState
import com.campusmaps.routing.Formats
import com.campusmaps.ui.screens.AddShortcutScreen
import com.campusmaps.ui.screens.DebugLink
import com.campusmaps.ui.screens.DebugOverlay
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

    val systemDark = isSystemInDarkTheme()
    val alwaysDark = screen == Screen.GUIDANCE || screen == Screen.GLASSES
    val screenDark = when (screen) {
        Screen.GUIDANCE, Screen.GLASSES -> true
        Screen.ROUTES -> systemDark || settings.demoMode
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

    BackHandler(enabled = screen != Screen.DESTINATION) { vm.back() }

    CampusMapsTheme(darkTheme = screenDark) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(targetState = screen, animationSpec = tween(250), label = "screen") { target ->
                when (target) {
                    Screen.DESTINATION -> DestinationScreen(
                        state = trip,
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
                        GuidanceScreen(state = it, arOverride = arOverride, onEndRoute = vm::endGuidance, onDone = vm::done)
                    }
                    Screen.GLASSES -> guidance?.let {
                        GlassesScreen(
                            state = it,
                            glasses = GlassesUi(glassesConnected, glassesPhase, glassesSeen, glassesStill),
                            showFakeStep = !settings.demoMode,
                            onRepeat = vm::repeatInstruction,
                            onStop = vm::endGuidance,
                            onFakeStep = vm::debugSkipStep,
                        )
                    }
                    Screen.ADD_SHORTCUT -> AddShortcutScreen(shortcutVm, onBack = vm::back)
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
                val lines = buildList {
                    add("screen: ${screen.name.lowercase()}")
                    add("clock: ${app.clock.label()}")
                    add("ar: ${arOverride.label}  tts: ${tts.name.lowercase()}")
                    add("network: ${if (offline) "offline" else "online"}")
                    if (g != null) {
                        add("pose: x=${"%.1f".format(g.pose.position.x)} y=${"%.1f".format(g.pose.position.y)} ${Formats.floorShort(g.floor)}")
                        add("heading: ${Math.toDegrees(g.pose.headingRad).toInt()} deg  conf: ${"%.2f".format(g.pose.confidence)}")
                        add("step: ${g.progress.stepIndex + 1}/${g.route.steps.size} ${g.step.kind.name.lowercase()}")
                        add("along: ${"%.1f".format(g.progress.alongM)} m  off: ${"%.1f".format(g.progress.offRouteM.coerceAtMost(999.0))} m")
                        add("reroutes: ${g.rerouteCount}  speed: x${vm.debugSpeed().toInt()}")
                    }
                }
                val links = buildList {
                    add(DebugLink("Cycle simulated time", vm::debugCycleClock))
                    add(DebugLink("AR: ${arOverride.label} (tap to change)", vm::debugCycleAr))
                    if (g != null) {
                        add(DebugLink("Drop confidence (Locate me)", vm::debugDropConfidence))
                        add(DebugLink("Force reroute", vm::debugPushOffRoute))
                        add(DebugLink("Skip step", vm::debugSkipStep))
                        add(DebugLink("Walk speed x1 / x4", vm::debugToggleSpeed))
                    }
                    add(DebugLink("Glasses: ${if (glassesConnected) "connected" else "unplugged"}", vm::debugToggleGlasses))
                    add(DebugLink("Offline: ${if (offline) "on" else "off"}", vm::debugToggleOffline))
                    add(DebugLink("Approve my pending shortcuts") { vm.debugReview(approve = true) })
                    add(DebugLink("Reject my pending shortcuts") { vm.debugReview(approve = false) })
                }
                DebugOverlay(guidance = g, lines = lines, links = links, onClose = vm::hideDebug)
            }
        }
    }
}

// Stand-in flow while there is no guidance session.
private val NoGuidance = MutableStateFlow<GuidanceState?>(null)
