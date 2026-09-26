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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.campusmaps.ui.theme.Sora
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

// Design tokens from the style guide (board 02 / 03).
private object Explore {
    val clayDeep = Color(0xFFA85F33)
    val sand = Color(0xFFEDD6C8)
    val ink = Color(0xFF313131)
    val cream = Color(0xFFF9F2ED)
    val mist = Color(0xFFE3E3E3)
    val muted = Color(0xFF6E6A67)
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
    val sheetDp = if (state.selected != null) 420 else 140

    Box(Modifier.fillMaxSize().background(Explore.cream).testTag("explore")) {
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
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = (sheetDp + 16).dp).navigationBarsPadding(),
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

        Sheet(state, actions, Modifier.align(Alignment.BottomCenter))
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
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Explore.clayDeep else Color.White,
        shadowElevation = 2.dp,
    ) {
        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = text(13, FontWeight.SemiBold, if (selected) Color.White else Explore.ink))
    }
}

@Composable
private fun HintCard(message: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Explore.sand, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Explore.clayDeep)
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
    Surface(onClick = onClick, shape = CircleShape, color = Explore.clayDeep, shadowElevation = 6.dp,
        modifier = Modifier.size(56.dp).testTag("explore-ar-fab")) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.ViewInAr, contentDescription = "Start AR navigation", tint = Color.White, modifier = Modifier.size(22.dp))
            Text("AR", style = text(11, FontWeight.Bold, Color.White))
        }
    }
}

@Composable
private fun Sheet(state: ExploreUiState, actions: ExploreActions, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Explore.cream,
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier.navigationBarsPadding().heightIn(max = 460.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).background(Explore.mist, CircleShape))
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
                Text(it, style = text(14, FontWeight.SemiBold, Color(0xFFB3261E)))
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
            Button(
                onClick = { actions.onStartAr(plan) },
                colors = ButtonDefaults.buttonColors(containerColor = Explore.clayDeep, contentColor = Color.White),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("explore-start-ar"),
            ) { Text("Start AR navigation", style = text(16, FontWeight.Bold, Color.White)) }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(Explore.sand.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = text(17, FontWeight.ExtraBold, Explore.ink), textAlign = TextAlign.Center)
        Text(label, style = text(12, FontWeight.Normal, Explore.muted))
    }
}

@Composable
private fun StepRow(n: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).border(1.5.dp, Explore.clayDeep, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = text(12, FontWeight.Bold, Explore.clayDeep))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = text(15, FontWeight.Normal, Explore.ink), modifier = Modifier.padding(top = 2.dp))
    }
}

private fun text(size: Int, weight: FontWeight, color: Color) =
    TextStyle(fontFamily = Sora, fontWeight = weight, fontSize = size.sp, color = color)
