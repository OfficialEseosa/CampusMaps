package com.campusmaps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded._3dRotation
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.route.Formats
import com.campusmaps.route.LockedNotice
import com.campusmaps.route.Route
import com.campusmaps.route.RouteCardText
import com.campusmaps.route.RouteOption
import com.campusmaps.route.RoutePlan
import com.campusmaps.ui.GuideMode
import com.campusmaps.ui.TripUiState
import com.campusmaps.ui.components.AppTopBar
import com.campusmaps.ui.components.IconTile
import com.campusmaps.ui.components.PrimaryPillButton
import com.campusmaps.ui.components.SmallTag
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.AppTextStyles
import com.campusmaps.ui.theme.CampusPalette
import com.campusmaps.ui.theme.CampusPalettes
import com.campusmaps.ui.theme.LocalCampusPalette
import com.campusmaps.ui.theme.Space

class RouteOptionsActions(
    val onBack: () -> Unit,
    val onReset: () -> Unit,
    val onSettings: () -> Unit,
    val onAvoidStairs: (Boolean) -> Unit,
    val onPickRoute: (RouteOption) -> Unit,   // Old: tap a card to start. Kept for callers; cards now select.
    val onAlreadyHere: (Route) -> Unit,
    val onGlasses: () -> Unit,                // Old "Guide me with glasses" button. Now the Glasses mode tile + Start.
    val onTitleLongPress: () -> Unit,
    val onSelectRoute: (RouteOption) -> Unit,
    val onGuideMode: (GuideMode) -> Unit,
    val onStart: () -> Unit,
    val onPreview: () -> Unit,
)

// Guide modes as the redesign shows them: icon, label, sub line.
private data class ModeSpec(val mode: GuideMode, val icon: ImageVector, val label: String, val sub: String)

private val MODES = listOf(
    ModeSpec(GuideMode.PHONE, Icons.Rounded.ViewInAr, "Phone AR", "Camera arrows"),
    ModeSpec(GuideMode.GLASSES, AppIcons.eyeglasses, "Glasses", "Spoken turns"),
    ModeSpec(GuideMode.MAP, Icons.Rounded.Map, "Map", "2D only"),
)

private val PreviewCardBg = Color(0xFF141110)

// S1b Route options (section 5 of the handoff; redesign "04 Route options").
// Tapping a card selects it; the Start pill at the bottom starts the selected route in the chosen guide mode.
@Composable
fun RouteOptionsScreen(state: TripUiState, guideMode: GuideMode, selectedRouteId: String?, actions: RouteOptionsActions) {
    val destination = state.destination ?: return
    val plan = state.plan
    val palette = LocalCampusPalette.current
    // Selected = the card with the stored id, else the fastest (first) one.
    val selected: RouteOption? = (plan as? RoutePlan.Options)?.options?.let { opts ->
        opts.firstOrNull { it.id == selectedRouteId } ?: opts.firstOrNull()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.surface),
    ) {
        AppTopBar(
            title = "Route options",
            onBack = actions.onBack,
            onSettings = actions.onSettings,
            onTitleLongPress = if (state.settings.demoMode) null else actions.onTitleLongPress,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = Space.xl, end = Space.xl, top = Space.s, bottom = Space.l),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "To ${destination.name}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
                        color = palette.ink,
                    )
                    Text(
                        "${Formats.floorLong(destination.floor)} · ${state.building.name}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = palette.muted,
                    )
                    Text(fromLabel(state.start), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = palette.muted)
                    state.simulatedTimeLabel?.let { SimulatedTimeChip(it, Modifier.padding(top = 6.dp)) }
                }
            }

            if (plan is RoutePlan.Options) {
                plan.lockedNotice?.let { notice -> item(key = "locked") { LockedBanner(notice) } }

                item(key = "preview") {
                    val route = selected?.route
                    val floors = state.building.floors
                    val entryFloor = route?.points?.firstOrNull { !it.node.isOutdoor }?.floor ?: floors.first
                    XrPreviewCard(
                        palette = palette,
                        floorCount = floors.last - floors.first + 1,
                        onClick = actions.onPreview,
                    ) {
                        com.campusmaps.ui.preview.FloorStack(
                            floors = floors,
                            entryFloor = entryFloor,
                            destFloor = destination.floor,
                            glow = palette.glow,
                            glowFill = palette.glowFill,
                            modifier = Modifier.size(170.dp, 150.dp),
                            compact = true,
                            route = route,
                        )
                    }
                }

                item(key = "modes") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Guide me with",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
                            color = palette.muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MODES.forEach { spec ->
                                ModeTile(
                                    spec = spec,
                                    selected = spec.mode == guideMode,
                                    palette = palette,
                                    onClick = { actions.onGuideMode(spec.mode) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(if (spec.mode == GuideMode.GLASSES) Modifier.testTag("glassesButton") else Modifier),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "avoid") {
                AvoidStairsSwitch(checked = state.settings.avoidStairs, onChange = actions.onAvoidStairs, palette = palette)
            }

            when (plan) {
                is RoutePlan.Options -> {
                    val best = plan.options.first()
                    // Keys + animateItem: flipping "Avoid stairs" reorders and fades the cards (about 250 ms).
                    items(plan.options, key = { it.id }) { option ->
                        RouteCard(
                            option = option,
                            best = best,
                            isBest = option === best,
                            isSelected = option === selected,
                            startsInside = state.startsInside,
                            startFloor = state.start.floor,
                            palette = palette,
                            onClick = { actions.onSelectRoute(option) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = androidx.compose.animation.core.tween(250),
                                placementSpec = androidx.compose.animation.core.tween(250),
                                fadeOutSpec = androidx.compose.animation.core.tween(250),
                            ),
                        )
                    }
                }
                is RoutePlan.AlreadyHere -> item(key = "here") { AlreadyHereCard(destination.name) { actions.onAlreadyHere(plan.route) } }
                is RoutePlan.NoRoute -> item(key = "none") { NoRouteState(plan.message, actions.onBack) }
                null -> Unit
            }
        }

        // Start bar pinned at the bottom (only when there is a route to follow; AlreadyHere starts from its card).
        if (plan is RoutePlan.Options && selected != null) {
            val spec = MODES.first { it.mode == guideMode }
            Column(Modifier.background(palette.surface)) {
                HorizontalDivider(thickness = 1.dp, color = palette.cardBorder)
                Box(
                    Modifier
                        .navigationBarsPadding()
                        .padding(start = Space.xl, end = Space.xl, top = 10.dp, bottom = Space.xl),
                ) {
                    StartPill(spec = spec, eta = Formats.eta(selected.etaSeconds), palette = palette, onClick = actions.onStart)
                }
            }
        }
    }
}

// Dark "XR PREVIEW" card: a small floor stack on the left, the pitch on the right. Opens the 3D preview.
@Composable
private fun XrPreviewCard(palette: CampusPalette, floorCount: Int, onClick: () -> Unit, stack: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(PreviewCardBg)
            .clickable(onClickLabel = "Open 3D view", onClick = onClick)
            .testTag("previewCard"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(170.dp, 150.dp)) { stack() }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "XR PREVIEW",
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = palette.glow,
            )
            Text(
                "See the route through $floorCount floors",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp),
                color = Color.White,
            )
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded._3dRotation, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                Text("Open 3D view", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Color.White)
            }
        }
    }
}

// One of the three "Guide me with" tiles. Selected = accent fill with a 2 dp accent border.
@Composable
private fun ModeTile(spec: ModeSpec, selected: Boolean, palette: CampusPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val fg = if (selected) palette.onAccent else palette.ink
    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(shape)
            .background(if (selected) palette.accent else palette.card)
            .border(if (selected) BorderStroke(2.dp, palette.accent) else BorderStroke(1.dp, palette.cardBorder), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(spec.icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(spec.label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = fg, maxLines = 1)
            Text(spec.sub, style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium), color = fg.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

// "Avoid stairs" row, 48 dp. Track is the campus line colour when on, the shared warm grey when off.
@Composable
private fun AvoidStairsSwitch(checked: Boolean, onChange: (Boolean) -> Unit, palette: CampusPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AppIcons.accessible, contentDescription = null, tint = palette.ink, modifier = Modifier.size(22.dp))
        Text(
            "Avoid stairs",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = palette.ink,
            modifier = Modifier.weight(1f),
        )
        // onCheckedChange = null: the row handles the tap, so there is one target, not two.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.line,
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = CampusPalettes.switchOff,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

// Bottom pill: "<mode icon> Start with Phone AR" on the left, the selected route's ETA in a chip on the right.
@Composable
private fun StartPill(spec: ModeSpec, eta: String, palette: CampusPalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = palette.accent,
        contentColor = palette.onAccent,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("startButton"),
    ) {
        Row(
            Modifier.padding(start = 24.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(spec.icon, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(22.dp))
                Text("Start with ${spec.label}", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = palette.onAccent)
            }
            Box(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(palette.chip)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(eta, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = palette.onAccent)
            }
        }
    }
}

// Honest chip: "Routed for Sat 21:00 (simulated)". Only when the clock is simulated.
@Composable
private fun SimulatedTimeChip(dayTime: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(AppIcons.schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
        Text(
            "Routed for $dayTime (simulated)",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

// Information, not an error. Never red.
@Composable
private fun LockedBanner(notice: LockedNotice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("lockedBanner"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AppIcons.lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Heads up: ") }
                append(notice.text)
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

// One route card. The whole card is the tap target and selects the route (Start begins it).
// The best card stands out by more than color: soft fill, "FASTEST" tag, dark tile and a bigger ETA.
// The selected card gets the 2 dp line border and a shadow.
@Composable
private fun RouteCard(
    option: RouteOption,
    best: RouteOption,
    isBest: Boolean,
    isSelected: Boolean,
    startsInside: Boolean,
    startFloor: Int,
    palette: CampusPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = RouteCardText.title(option, startsInside)
    val subtitle = RouteCardText.subtitle(option, startsInside, startFloor)
    val eta = Formats.eta(option.etaSeconds)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (isBest) palette.soft else palette.card,
        contentColor = palette.ink,
        border = if (isSelected) BorderStroke(2.dp, palette.line) else BorderStroke(1.dp, palette.cardBorder),
        shadowElevation = if (isSelected) 6.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("routeCard_${option.id}")
            .semantics {
                contentDescription = "$title, $subtitle, $eta" + (if (isBest) ", fastest" else "") + (if (isSelected) ", selected" else "")
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(
                    icon = AppIcons.forMethod(option.method),
                    container = if (isBest) palette.line else palette.cardBorder,
                    content = if (isBest) Color.White else palette.ink,
                    size = 46.dp,
                    corner = 14.dp,
                    iconSize = 26.dp,
                    contentDescription = RouteCardText.methodWords(option.method),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (isBest || option.usesStudentShortcut) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isBest) SmallTag("FASTEST", container = palette.line, content = Color.White)
                            if (option.usesStudentShortcut) {
                                SmallTag("Student shortcut", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer, icon = AppIcons.addRoad)
                            }
                        }
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = palette.ink)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = palette.muted)
                }
                Text(
                    eta,
                    style = (if (isBest) AppTextStyles.etaBest else AppTextStyles.etaOther).copy(fontSize = if (isBest) 32.sp else 26.sp, letterSpacing = (-1).sp),
                    color = palette.ink,
                )
            }
            HorizontalDivider(thickness = 1.dp, color = palette.cardBorder)
            val lineStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
            if (isBest) {
                Column {
                    Text(RouteCardText.bestSummary(option), style = lineStyle, color = palette.muted)
                    RouteCardText.alsoVia(option)?.let { Text(it, style = lineStyle, color = palette.muted) }
                }
            } else {
                Text(RouteCardText.difference(option, best), style = lineStyle, color = palette.muted)
            }
        }
    }
}

// Start equals destination: one card. Tapping it starts right away (no Start bar here).
@Composable
private fun AlreadyHereCard(roomName: String, onClick: () -> Unit) {
    val palette = LocalCampusPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = palette.soft,
        contentColor = palette.ink,
        border = BorderStroke(2.dp, palette.line),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(AppIcons.checkCircle, container = palette.line, content = Color.White, size = 46.dp, corner = 14.dp, iconSize = 26.dp)
            Column {
                Text("You are already here", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = palette.ink)
                Text(roomName, style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
    }
}

// No route: centered icon, the sentence in error color, and one way out.
@Composable
private fun NoRouteState(message: String, onPickAnother: () -> Unit) {
    val locked = message.contains("card-only")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (locked) AppIcons.lock else AppIcons.signpost,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("noRoute"),
        )
        PrimaryPillButton(text = "Pick another room", onClick = onPickAnother, height = 56.dp)
    }
}
