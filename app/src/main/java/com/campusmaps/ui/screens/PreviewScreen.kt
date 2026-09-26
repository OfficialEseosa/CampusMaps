package com.campusmaps.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded._3dRotation
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.route.RouteOption
import com.campusmaps.ui.TripUiState
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.preview.FloorStack
import com.campusmaps.ui.theme.ArOverlayColors
import com.campusmaps.ui.theme.LocalCampusPalette
import com.campusmaps.ui.theme.Sora

// Design "05 3D preview" (CampusMaps.dc.html): the building as a stack of floors with the chosen route on it.
// Drag to rotate (horizontal = spin, vertical = tilt 10..78), floor chips focus one floor, "Top" and
// "Drag to rotate" snap to the two preset views. Always dark, like S2 and S3.
private val PreviewTop = Color(0xFF2A2420)
private val PreviewBottom = Color(0xFF0F0D0C)
private val PreviewEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

@Composable
fun PreviewScreen(state: TripUiState, option: RouteOption, onBack: () -> Unit, onStart: () -> Unit) {
    val palette = LocalCampusPalette.current
    val building = state.building
    val route = option.route
    val entryFloor = route.points.firstOrNull { !it.node.isOutdoor }?.floor ?: building.floors.first
    val destination = state.destination ?: route.destination
    val destFloor = destination.floor
    val floorCount = building.floors.last - building.floors.first + 1

    var rotation by remember { mutableFloatStateOf(-38f) }
    var tilt by remember { mutableFloatStateOf(58f) }
    var dragging by remember { mutableStateOf(false) }
    var focusFloor by remember { mutableStateOf<Int?>(null) }
    val spec = if (dragging) snap<Float>() else tween<Float>(600, easing = PreviewEase)
    val shownRotation by animateFloatAsState(rotation, spec, label = "rotation")
    val shownTilt by animateFloatAsState(tilt, spec, label = "tilt")
    val density = LocalDensity.current.density

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(PreviewTop, PreviewBottom)))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.arrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(building.name, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$floorCount floors · route to ${destination.name}",
                    color = ArOverlayColors.textMuted, fontFamily = Sora, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Stage
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, delta ->
                        change.consume()
                        rotation += delta.x / density * 0.5f
                        tilt = (tilt - delta.y / density * 0.3f).coerceIn(10f, 78f)
                    }
                },
        ) {
            FloorStack(
                floors = building.floors,
                entryFloor = entryFloor,
                destFloor = destFloor,
                glow = palette.glow,
                glowFill = palette.glowFill,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 56.dp, bottom = 48.dp)
                    .semantics { contentDescription = "3D view of ${building.name}, route to ${destination.name} on floor $destFloor" },
                rotationDeg = shownRotation,
                tiltDeg = shownTilt,
                focusFloor = focusFloor,
                route = route,
            )

            // Floor chips, top floor first
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (floor in building.floors.reversed()) {
                    val on = focusFloor == floor
                    val isDest = floor == destFloor
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .size(width = 48.dp, height = 40.dp)
                            .clip(shape)
                            .background(if (on) Color.White else if (isDest) palette.glowFill else Color.White.copy(alpha = 0.06f))
                            .border(1.dp, if (isDest) palette.glow else Color.White.copy(alpha = 0.18f), shape)
                            .clickable(role = Role.Button) { focusFloor = if (on) null else floor }
                            .semantics { contentDescription = if (on) "Show all floors" else "Show floor $floor only" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("F$floor", color = if (on) Color(0xFF111111) else Color.White, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
            }

            // View presets
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ViewPill(Icons.Rounded.GridView, "Top") { tilt = 12f; rotation = 0f }
                ViewPill(Icons.Rounded._3dRotation, "Drag to rotate") { tilt = 58f; rotation = -38f }
            }
        }

        // Legend and start
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendItem("Entrance") { Box(Modifier.size(10.dp).clip(CircleShape).background(ArOverlayColors.user)) }
                LegendItem("Your route") { Box(Modifier.size(width = 14.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(ArOverlayColors.arrowCore)) }
                LegendItem(destination.name, Modifier.weight(1f, fill = false)) { Box(Modifier.size(10.dp).border(2.5.dp, palette.glow, CircleShape)) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.accent)
                    .clickable(role = Role.Button, onClick = onStart),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start AR guidance", color = palette.onAccent, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ViewPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .height(36.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Text(label, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun LegendItem(label: String, modifier: Modifier = Modifier, swatch: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        Text(label, color = ArOverlayColors.textMuted, fontFamily = Sora, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
