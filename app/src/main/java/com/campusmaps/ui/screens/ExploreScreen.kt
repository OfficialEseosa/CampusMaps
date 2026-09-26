package com.campusmaps.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.outdoor.EntrancePlan
import com.campusmaps.outdoor.ExploreUiState
import com.campusmaps.outdoor.ExploreViewModel
import com.campusmaps.outdoor.OutdoorRoutes
import com.campusmaps.outdoor.RouteSource
import com.campusmaps.route.Formats
import com.campusmaps.ui.map.GoogleOutdoorMap
import com.campusmaps.ui.map.rememberExploreCamera
import com.campusmaps.ui.theme.LocalCampusPalette
import com.campusmaps.ui.theme.CampusMapsTheme
import com.campusmaps.ui.theme.CampusPalettes
import com.campusmaps.data.campus.Campuses
import androidx.compose.ui.layout.onSizeChanged
import com.campusmaps.ui.theme.Sora
import com.campusmaps.ui.components.PrimaryPillButton
import androidx.compose.foundation.BorderStroke
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

// Campus skin (CampusTheme.kt), same tokens as S1 and S1b: accent for the primary button and the selected chip,
// card / cardBorder for tiles, line for step numbers, surface for the sheet.
private object Explore {
    val accent @Composable get() = LocalCampusPalette.current.accent
    val onAccent @Composable get() = LocalCampusPalette.current.onAccent
    val line @Composable get() = LocalCampusPalette.current.line
    val soft @Composable get() = LocalCampusPalette.current.soft
    val card @Composable get() = LocalCampusPalette.current.card
    val cardBorder @Composable get() = LocalCampusPalette.current.cardBorder
    val ink @Composable get() = LocalCampusPalette.current.ink
    val surface @Composable get() = LocalCampusPalette.current.surface
    val muted @Composable get() = LocalCampusPalette.current.muted
}

data class ExploreActions(
    val onSearch: () -> Unit,          // Opens S1 Destination
    val onSettings: () -> Unit,
    val onStartAr: (EntrancePlan) -> Unit, // Hands off to S2 with the same destination and entrance
)

// Explore (leg 1): a top-down Google map with the walk from the student to the recommended entrance, and a sheet
// with the room, distance, minutes, entrance floor and the first steps (design boards 02 and 03).
@Composable
fun ExploreScreen(vm: ExploreViewModel, actions: ExploreActions) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.onPermission(r.values.any { it })
    }
    LaunchedEffect(Unit) {
        if (vm.location.hasPermission()) vm.onVisible()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    DisposableEffect(Unit) { onDispose { vm.onHidden() } }

    val center = state.fix?.point ?: ExploreViewModel.CAMPUS_CENTER
    val camera = rememberExploreCamera(center)
    // qa-phone N4: the camera opens on the campus centre when there is no fix yet. On the first fix, frame the dot and the
    // entrance (or the dot alone without a plan) once; after that the camera is the user's.
    var framedFirstFix by remember { mutableStateOf(state.fix != null) }
    LaunchedEffect(state.fix != null) {
        val f = state.fix ?: return@LaunchedEffect
        if (framedFirstFix) return@LaunchedEffect
        framedFirstFix = true
        val dot = LatLng(f.lat, f.lng)
        val door = state.plan?.entrance
        val update = if (door == null) CameraUpdateFactory.newLatLngZoom(dot, 17f)
        else CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().include(dot).include(LatLng(door.lat, door.lng)).build(), 160)
        try { camera.animate(update, 600) } catch (_: Exception) { }
    }
    // The sheet's real height (it grows with the steps, up to 460 dp plus the nav bar); the map padding and the
    // round buttons sit above it so the AR button is never covered.
    val density = androidx.compose.ui.platform.LocalDensity.current
    var sheetPx by remember { mutableStateOf(0) }
    val sheetDp = if (sheetPx > 0) with(density) { sheetPx.toDp().value.toInt() } else if (state.selected != null) 420 else 140

    // Skin follows the picked room's campus (KL = Georgia Tech, CS / CSE = Georgia State), like S0b and S1.
    val skin = state.selected?.let { CampusPalettes.of(Campuses.of(it.buildingId).id) } ?: LocalCampusPalette.current
    CampusMapsTheme(darkTheme = false, campus = skin) {
    Box(Modifier.fillMaxSize().background(Explore.surface).testTag("explore")) {
        GoogleOutdoorMap(
            camera = camera,
            user = state.fix,
            route = state.route?.points.orEmpty(),
            entrance = state.plan?.entrance,
            satellite = state.satellite,
            bottomPaddingDp = sheetDp,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TopSearch(onSearch = actions.onSearch, onSettings = actions.onSettings)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.destinations.forEach { d ->
                    Chip(d.label, selected = state.selected == d) { vm.select(d.buildingId, d.nodeId) }
                }
            }
            if (!state.hasPermission) {
                Spacer(Modifier.height(10.dp))
                HintCard("Turn on location to see your route") {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = (sheetDp + 16).dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            RoundButton(Icons.Filled.Layers, if (state.satellite) "Show standard map" else "Show satellite", vm::toggleSatellite)
            RoundButton(Icons.Filled.MyLocation, "Show my location") {
                val f = state.fix ?: return@RoundButton
                scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(f.lat, f.lng), 17.5f), 400) }
            }
            state.plan?.let { plan -> ArFab { actions.onStartAr(plan) } }
        }

        Sheet(state, actions, Modifier.align(Alignment.BottomCenter).onSizeChanged { sheetPx = it.height })
    }
    }
}

@Composable
private fun TopSearch(onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onSearch,
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier.weight(1f).height(52.dp).testTag("explore-search"),
        ) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Explore.muted)
                Spacer(Modifier.width(10.dp))
                Text("Search a room", style = text(15, FontWeight.Normal, Explore.muted))
            }
        }
        Spacer(Modifier.width(10.dp))
        RoundButton(Icons.Filled.Settings, "Settings", onSettings)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Explore.accent else Color.White,
        border = if (selected) null else BorderStroke(1.dp, Explore.cardBorder),
        shadowElevation = 2.dp,
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = text(13, FontWeight.Bold, if (selected) Explore.onAccent else Explore.ink))
        }
    }
}

@Composable
private fun HintCard(message: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Explore.soft,
        border = BorderStroke(1.dp, Explore.cardBorder), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Explore.line)
            Spacer(Modifier.width(10.dp))
            Text(message, style = text(14, FontWeight.SemiBold, Explore.ink))
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White, shadowElevation = 4.dp, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, tint = Explore.ink) }
    }
}

@Composable
private fun ArFab(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Explore.accent, shadowElevation = 6.dp,
        modifier = Modifier.size(56.dp).testTag("explore-ar-fab")) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.ViewInAr, contentDescription = "Start AR navigation", tint = Explore.onAccent, modifier = Modifier.size(22.dp))
            Text("AR", style = text(11, FontWeight.Bold, Explore.onAccent))
        }
    }
}

@Composable
private fun Sheet(state: ExploreUiState, actions: ExploreActions, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Explore.surface,
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier.navigationBarsPadding().heightIn(max = 460.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).background(Explore.cardBorder, CircleShape))
            Spacer(Modifier.height(12.dp))
            val sel = state.selected
            val plan = state.plan
            if (sel == null) {
                Text("Where to?", style = text(20, FontWeight.ExtraBold, Explore.ink))
                Text("Pick a room above or search", style = text(14, FontWeight.Normal, Explore.muted))
                return@Column
            }
            Text(sel.name, style = text(24, FontWeight.ExtraBold, Explore.ink), modifier = Modifier.testTag("explore-room"))
            val building = plan?.buildingName ?: sel.buildingCode
            val floor = plan?.destinationFloor?.let { " · ${Formats.floorLong(it)}" }.orEmpty()
            Text("$building$floor · ${state.destinationKind}", style = text(14, FontWeight.Normal, Explore.muted))
            state.noRoute?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = text(14, FontWeight.SemiBold, androidx.compose.material3.MaterialTheme.colorScheme.error))
                return@Column
            }
            if (plan == null) return@Column
            Spacer(Modifier.height(14.dp))
            val route = state.route
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(route?.let { Formats.metres(it.distanceM) } ?: "--", "Distance", Modifier.weight(1f))
                Tile(route?.let { "${it.minutes} min" } ?: "--", "Walking", Modifier.weight(1f))
                Tile(Formats.floorLong(plan.entranceFloor), "Entrance", Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            val note = buildList {
                add("Enter at the ${OutdoorRoutes.shortName(plan.entranceName)}")
                if (plan.approximate) add("(approximate)")
                if (route?.source == RouteSource.STRAIGHT_LINE) add("· straight line")
            }.joinToString(" ")
            Text(note, style = text(12, FontWeight.Normal, Explore.muted), modifier = Modifier.testTag("explore-entrance"))
            Spacer(Modifier.height(12.dp))
            state.steps.forEachIndexed { i, s -> StepRow(i + 1, s) }
            Spacer(Modifier.height(14.dp))
            PrimaryPillButton(
                text = "Start AR navigation",
                onClick = { actions.onStartAr(plan) },
                height = 56.dp,
                icon = Icons.Filled.ViewInAr,
                modifier = Modifier.testTag("explore-start-ar"),
            )
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    // Same card as S1b's route cards: card fill, 1 dp cardBorder, 18 dp corners.
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.background(Explore.card, shape).border(1.dp, Explore.cardBorder, shape).padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = text(17, FontWeight.ExtraBold, Explore.ink), textAlign = TextAlign.Center, maxLines = 1)
        Text(label, style = text(12, FontWeight.Medium, Explore.muted), maxLines = 1)
    }
}

@Composable
private fun StepRow(n: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).border(1.5.dp, Explore.line, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = text(12, FontWeight.Bold, Explore.line))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = text(15, FontWeight.Normal, Explore.ink), modifier = Modifier.padding(top = 2.dp))
    }
}

private fun text(size: Int, weight: FontWeight, color: Color) =
    TextStyle(fontFamily = Sora, fontWeight = weight, fontSize = size.sp, color = color)
