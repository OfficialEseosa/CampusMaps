package com.campusmaps.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.campusmaps.guidance.GuidanceState
import com.campusmaps.platform.ArOverride
import com.campusmaps.platform.ArSupport
import com.campusmaps.route.Formats
import com.campusmaps.route.StepKind
import com.campusmaps.ui.ar.ArWorldOverlay
import com.campusmaps.ui.ar.toArRouteInput
import com.campusmaps.ui.ar.CameraPreview
import com.campusmaps.ui.ar.PaintedHallway
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.map.CampusMap
import com.campusmaps.ui.map.MapViewState
import com.campusmaps.ui.theme.AppTextStyles
import com.campusmaps.ui.theme.ArOverlayColors
import com.campusmaps.ui.theme.LightColors
import com.campusmaps.ui.theme.Sora

private val BannerIconDark = Color(0xFF141110)

// S2 AR guidance (section 6) plus its Arrived state (section 7). Always uses ArOverlayColors.
// Keeps the middle 60% of the screen clear for the floor arrows.
@Composable
fun GuidanceScreen(
    state: GuidanceState,
    arOverride: ArOverride,
    onEndRoute: () -> Unit,
    onDone: () -> Unit,
    buildingToWorld: com.campusmaps.loc.BuildingToWorld? = null,
    onBuildingToWorld: (com.campusmaps.loc.BuildingToWorld?) -> Unit = {},
    /** Outdoor mode (w1/geo). Null keeps S2 exactly as before. */
    geo: com.campusmaps.geo.ArCoreGeospatialProvider? = null,
    /** The entrance the outdoor leg walks to (GeoEntrances.forRoute); null = banner and map only. */
    outdoorEntrance: com.campusmaps.geo.GeoEntrance? = null,
    /** FusedLocation distance to [outdoorEntrance] (HandoffUi.distanceToEntranceM); replaces the banner's "in X m" outdoors. */
    outdoorDistanceM: Double? = null,
) {
    val context = LocalContext.current
    val outdoorLeg = geo != null && state.startsOutside && !state.arrived
    OutdoorGeoEffect(geo, outdoorEntrance, outdoorLeg)
    val geoState = geo?.state?.collectAsState()?.value

    // Can this phone do AR, and may we use the camera?
    val arSupported by produceState<Boolean?>(initialValue = null) { value = ArSupport.isSupported(context) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    LaunchedEffect(arSupported, arOverride) {
        val wantsCamera = arOverride == ArOverride.FORCE_ON || (arOverride == ArOverride.AUTO && arSupported == true)
        if (wantsCamera && !cameraGranted) askCamera.launch(Manifest.permission.CAMERA)
    }
    val arOn = when (arOverride) {
        ArOverride.FORCE_ON -> true
        ArOverride.FORCE_OFF -> false
        ArOverride.AUTO -> arSupported == true && cameraGranted
    }
    val unavailableText = when {
        arOn || arSupported == null -> null
        arSupported == true && !cameraGranted && arOverride == ArOverride.AUTO -> "Camera is off, so AR is paused. Follow the text and the map."
        else -> "AR unavailable on this device. Follow the text and the map."
    }

    // Arrows fade out over 400 ms when we lose track, and back in when we recover. No flash.
    val arrowsAlpha by animateFloatAsState(if (state.locating) 0f else 1f, tween(400), label = "arrows")

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val top = max(44.dp, topInset + 4.dp)
    val bottom = 28.dp + bottomInset

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(if (arOn) Color(0xFF141110) else Color(0xFF2A2420))
            .testTag("guidanceScreen"),
    ) {
        val screenHeight = maxHeight
        var mapEnlarged by rememberSaveable { mutableStateOf(false) }

        // Camera image (or its stand-in) and the AR world objects.
        // Real ARCore + camera: Raphael's world-locked AR layer (SceneView ARScene, docs/05). Otherwise, or if the AR
        // session fails, the teammate's camera preview (or painted stand-in) with the screen-space ArWorldOverlay.
        var arFailed by remember { mutableStateOf<String?>(null) }
        val realAr = arOn && arSupported == true && cameraGranted && arOverride != ArOverride.FORCE_OFF && arFailed == null
        if (realAr) {
            com.campusmaps.ui.ar.ArGuidanceView(
                input = state.toArRouteInput(),
                buildingToWorld = buildingToWorld,
                onBuildingToWorld = onBuildingToWorld,
                onFail = { arFailed = it },
                modifier = Modifier.fillMaxSize(),
                // Keep the AR hint above the minimap (or the enlarged map), and drop it when Locate me or the
                // arrived buttons take that space; it overlapped both on the S25 (docs/22 #4).
                hintBottom = bottom + 16.dp + if (mapEnlarged) screenHeight / 2 else 170.dp,
                showHints = !state.arrived && !state.locating && !outdoorLeg,
                outdoor = geo.takeIf { outdoorLeg },
            )
        } else if (arOn) {
            if (cameraGranted) CameraPreview(Modifier.fillMaxSize()) else PaintedHallway(Modifier.fillMaxSize())
            ArWorldOverlay(state, arrowsAlpha, Modifier.fillMaxSize())
        }

        // Amber brackets around the sign being read (only when there is a camera image to read from).
        state.pose.sign?.takeIf { state.locating && arOn }?.let { sign -> SignBrackets(sign.left, sign.top, sign.right, sign.bottom) }

        // ---------- Top ----------
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = top),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (geo != null && !state.arrived) {
                // Board 04 chip: outdoors the Geospatial state, indoors (after the entrance) the current node.
                if (outdoorLeg && geoState != null) com.campusmaps.ui.ar.TrackingChip(
                    com.campusmaps.geo.OutdoorArrowGate.chipText(geoState), com.campusmaps.geo.OutdoorArrowGate.shouldDrawArrows(geoState))
                else com.campusmaps.ui.ar.TrackingChip(com.campusmaps.ui.ar.nearestRouteNodeName(state), live = !state.locating)
            }
            Crossfade(targetState = state.arrived, animationSpec = tween(350), label = "banner") { arrived ->
                if (arrived) {
                    ArrivedBanner(state)
                } else if (state.startsOutside) {
                    // Explore starts carry their own GPS distance per street step (state.distanceToStepM, the same number
                    // the watch gets); the entrance distance override is only for routes from S1b's fixed start points.
                    CompactBanner(state, onEndRoute, outdoorDistanceM.takeIf { geo != null && state.step.outdoorEnd == null })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TopRow(state, onEndRoute)
                        InstructionBanner(state)
                    }
                }
            }
            if (state.startsOutside && !state.arrived) {
                RerouteChip(state)
            }
            if (unavailableText != null && !state.arrived) {
                // Must look intentional, not broken: a calm info card with an icon.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ArOverlayColors.scrim)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("arUnavailable"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.signpost, contentDescription = null, tint = ArOverlayColors.arrowCore, modifier = Modifier.size(22.dp))
                    Text(unavailableText, color = ArOverlayColors.text, fontFamily = Sora, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ---------- Bottom ----------
        val mapState = MapViewState(
            building = state.building,
            floor = state.floor,
            route = state.route,
            user = state.pose.position,
            userHeadingRad = state.pose.headingRad,
            arrived = state.arrived,
            outdoors = state.startsOutside,
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.arrived) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Minimap(mapState, width = 120.dp, height = 120.dp, enlargeIcon = null, onClick = null)
                    FloorBadge(state.floor)
                }
                DoneButton(onDone)
                BackToRoutesButton(onEndRoute)
            } else {
                // Bigger map when there is no AR, or when the student tapped enlarge.
                val big = unavailableText != null
                val mapHeight by animateDpAsState(
                    when {
                        mapEnlarged -> screenHeight / 2
                        big -> 360.dp
                        else -> 170.dp
                    },
                    tween(300),
                    label = "mapHeight",
                )
                val fullWidth = mapEnlarged || big
                if (fullWidth) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.locating) LocatePrompt(state.destination.name)
                        Box {
                            Minimap(
                                mapState,
                                width = null,
                                height = mapHeight,
                                enlargeIcon = if (mapEnlarged) AppIcons.closeFullscreen else AppIcons.openInFull,
                                onClick = { mapEnlarged = !mapEnlarged },
                            )
                            FloorBadge(state.floor, Modifier.align(Alignment.BottomEnd).padding(10.dp))
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Minimap(mapState, width = 170.dp, height = mapHeight, enlargeIcon = AppIcons.openInFull, onClick = { mapEnlarged = true })
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (state.locating) LocatePrompt(state.destination.name)
                            FloorBadge(state.floor)
                        }
                    }
                }
            }
        }
    }
}

// Reroute chip (left) and End route (right). 48 dp row under the status bar.
@Composable
private fun TopRow(state: GuidanceState, onEndRoute: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { RerouteChip(state) }
        Surface(
            onClick = onEndRoute,
            shape = CircleShape,
            color = ArOverlayColors.scrim,
            contentColor = ArOverlayColors.text,
            modifier = Modifier
                .height(48.dp)
                .testTag("endRoute"),
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(AppIcons.close, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("End route", fontFamily = Sora, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// "Rerouted (1)": only after a reroute, fades out after about 3 s.
@Composable
private fun RerouteChip(state: GuidanceState) {
    AnimatedVisibility(visible = state.showRerouteChip, enter = fadeIn(tween(300)), exit = fadeOut(tween(400))) {
        Row(
            Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(ArOverlayColors.attention)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(AppIcons.replay, contentDescription = null, tint = ArOverlayColors.onAttention, modifier = Modifier.size(16.dp))
            Text("Rerouted (${state.rerouteCount})", color = ArOverlayColors.onAttention, fontFamily = Sora, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// The main instruction banner. Text is always on the scrim, never straight on the camera image.
@Composable
private fun InstructionBanner(state: GuidanceState) {
    val step = state.step
    // During an elevator or stairs ride there is nothing left to walk, so hide "in 0 m".
    val riding = (step.kind == StepKind.ELEVATOR || step.kind == StepKind.STAIRS) &&
        state.distanceToStepM < 0.5 && state.floor != step.completeFloor
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(ArOverlayColors.scrim)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("instructionBanner"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ArOverlayColors.arrowCore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.forStep(step.kind), contentDescription = null, tint = BannerIconDark, modifier = Modifier.size(34.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.bannerText, style = AppTextStyles.arInstruction, color = ArOverlayColors.text, modifier = Modifier.testTag("instructionText"))
                when {
                    riding -> Text("Riding to ${Formats.floorLong(step.completeFloor)}", style = AppTextStyles.arDistance, color = ArOverlayColors.textMuted)
                    // "in 0 m" read oddly under "Head toward Atrium centre" at the start (docs/22 #5): only show a
                    // distance that is at least half a metre.
                    state.distanceToStepM >= 0.5 ->
                        Text(Formats.inDistance(state.distanceToStepM), style = AppTextStyles.arDistance, color = ArOverlayColors.textMuted)
                }
            }
        }
        state.nextStep?.let { next ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.14f)),
            )
            Text("Then: ${next.text}", style = AppTextStyles.arThen, color = ArOverlayColors.textMuted)
        }
    }
}

// Starting outside: compact banner with a door tile and a round close button.
@Composable
private fun CompactBanner(state: GuidanceState, onEndRoute: () -> Unit, distanceOverrideM: Double? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ArOverlayColors.scrim)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("instructionBanner"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ArOverlayColors.arrowCore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.forStep(state.step.kind), contentDescription = null, tint = BannerIconDark, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                state.bannerText,
                color = ArOverlayColors.text,
                fontFamily = Sora,
                fontSize = 20.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("instructionText"),
            )
            Text(Formats.inDistance(distanceOverrideM ?: state.distanceToStepM), color = ArOverlayColors.textMuted, fontFamily = Sora, fontSize = 14.sp)
            // Street steps (Explore start): the next one, so the student sees the turn after this one.
            state.nextStep?.takeIf { state.step.outdoorEnd != null }?.let { next ->
                Text("Then: ${next.text}", style = AppTextStyles.arThen, color = ArOverlayColors.textMuted, maxLines = 2,
                    modifier = Modifier.testTag("thenText"))
            }
        }
        Surface(
            onClick = onEndRoute,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            contentColor = ArOverlayColors.text,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "End route" }
                .testTag("endRoute"),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(AppIcons.close, contentDescription = null, modifier = Modifier.size(20.dp)) }
        }
    }
}

// Arrived: confident color change and a clear check. No confetti.
@Composable
private fun ArrivedBanner(state: GuidanceState) {
    val room = state.destination.name
    val headline: String
    val detail: String?
    when {
        state.step.kind == StepKind.ALREADY_THERE -> { headline = state.step.text; detail = null }
        state.step.side != null -> { headline = "You have arrived"; detail = state.step.text }
        else -> { headline = "You have arrived at $room"; detail = null } // Never say "You have arrived" twice
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(ArOverlayColors.arrived)
            .padding(18.dp)
            .testTag("arrivedBanner"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(ArOverlayColors.onArrived),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.check, contentDescription = null, tint = ArOverlayColors.arrived, modifier = Modifier.size(34.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(headline, style = AppTextStyles.arrivedHeadline, color = ArOverlayColors.onArrived)
            detail?.let { Text(it, color = ArOverlayColors.onArrived, fontFamily = Sora, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// Minimap card. The whole card is tappable; the 32 dp "enlarge" button makes that visible.
@Composable
private fun Minimap(
    state: MapViewState,
    width: Dp?,
    height: Dp,
    enlargeIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: (() -> Unit)?,
) {
    val sizeModifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    Box(
        sizeModifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(ArOverlayColors.scrimMap)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Enlarge map", onClick = onClick) else Modifier)
            .testTag("minimap"),
    ) {
        CampusMap(state, Modifier.fillMaxSize(), compact = height <= 180.dp)
        if (enlargeIcon != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(enlargeIcon, contentDescription = null, tint = ArOverlayColors.text, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// Round floor badge: user blue, 3 dp white border, "F1". Counts up during an elevator ride.
@Composable
private fun FloorBadge(floor: Int, modifier: Modifier = Modifier) {
    Crossfade(targetState = floor, animationSpec = tween(250), label = "floor", modifier = modifier) { f ->
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ArOverlayColors.user)
                .border(3.dp, Color.White, CircleShape)
                .semantics { contentDescription = Formats.floorLong(f) }
                .testTag("floorBadge"),
            contentAlignment = Alignment.Center,
        ) {
            Text(Formats.floorShort(f), style = AppTextStyles.floorBadge, color = Color.White)
        }
    }
}

// Shown when confidence drops below 0.5: point the camera at a sign.
@Composable
private fun LocatePrompt(destinationName: String) {
    Column(
        Modifier
            .width(190.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ArOverlayColors.scrim)
            .border(2.dp, ArOverlayColors.attention, RoundedCornerShape(18.dp))
            .padding(12.dp)
            .testTag("locatePrompt"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.myLocation, contentDescription = null, tint = ArOverlayColors.attention, modifier = Modifier.size(18.dp))
            Text("Locate me", color = ArOverlayColors.attention, fontFamily = Sora, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("Point at a sign so I can find where you are.", color = ArOverlayColors.text, fontFamily = Sora, fontSize = 13.sp, lineHeight = 17.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                destinationName.uppercase(),
                color = Color.White,
                fontFamily = Sora,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1F6B45))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text("like this", color = ArOverlayColors.textMuted, fontFamily = Sora, fontSize = 11.sp)
        }
    }
}

// Amber corner brackets around the sign being read, plus a small "Reading sign..." pill.
@Composable
private fun SignBrackets(left: Float, top: Float, right: Float, bottom: Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val b = maxHeight * bottom
        Canvas(Modifier.fillMaxSize()) {
            val x0 = size.width * left
            val y0 = size.height * top
            val x1 = size.width * right
            val y1 = size.height * bottom
            val arm = 30.dp.toPx()
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            fun corner(cx: Float, cy: Float, dx: Float, dy: Float) {
                val path = Path().apply {
                    moveTo(cx, cy + dy * arm)
                    lineTo(cx, cy)
                    lineTo(cx + dx * arm, cy)
                }
                drawPath(path, ArOverlayColors.attention, style = stroke)
            }
            corner(x0, y0, 1f, 1f)
            corner(x1, y0, -1f, 1f)
            corner(x1, y1, -1f, -1f)
            corner(x0, y1, 1f, -1f)
        }
        Text(
            "Reading sign…",
            color = ArOverlayColors.onAttention,
            fontFamily = Sora,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = b + 10.dp, start = (maxWidth - 140.dp) / 2)
                .clip(CircleShape)
                .background(ArOverlayColors.attention)
                .padding(horizontal = 18.dp, vertical = 7.dp),
        )
    }
}

// "Done": the brand primary (light scheme on purpose), resets to S1.
@Composable
private fun DoneButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = LightColors.primary, contentColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .testTag("doneButton"),
    ) {
        Icon(AppIcons.check, contentDescription = null, modifier = Modifier.size(22.dp))
        Text("Done", fontFamily = Sora, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun BackToRoutesButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = ArOverlayColors.scrim, contentColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text("Back to routes", fontFamily = Sora, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// Outdoor mode (w1/geo): start Geospatial on the outdoor leg, check VPS and place the entrance Terrain anchor; stop it
// once the student is through the entrance and indoor localization takes over.
@Composable
private fun OutdoorGeoEffect(geo: com.campusmaps.geo.ArCoreGeospatialProvider?, entrance: com.campusmaps.geo.GeoEntrance?, outdoorLeg: Boolean) {
    if (geo == null) return
    // Geospatial and FusedLocation need location; ask once on the outdoor leg (the map screen should ask earlier, so the
    // AR session starts with Geospatial on; a grant here takes effect on the next AR session).
    val context = LocalContext.current
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        android.util.Log.i("Geo", "location permission ${if (ok) "granted" else "denied"}")
    }
    LaunchedEffect(outdoorLeg) {
        if (outdoorLeg && !com.campusmaps.geo.FusedLocationFixes.hasPermission(context)) askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    // Leaving S2 (End route, Done, back) closes the ARCore session: drop it, or the next S2 checks VPS on the dead one.
    DisposableEffect(geo) { onDispose { geo.stop() } }
    LaunchedEffect(geo, entrance, outdoorLeg) {
        if (outdoorLeg) {
            geo.start()
            if (entrance != null) {
                geo.checkVps(entrance.latLng.lat, entrance.latLng.lng)
                geo.placeTerrainAnchor(entrance.latLng.lat, entrance.latLng.lng)
            } else {
                android.util.Log.w("Geo", "outdoor leg without entrance lat/lng: banner and map only")
            }
        } else {
            geo.stop()
        }
    }
}
