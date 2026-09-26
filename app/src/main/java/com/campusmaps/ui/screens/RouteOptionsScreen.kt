package com.campusmaps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.routing.Formats
import com.campusmaps.routing.LockedNotice
import com.campusmaps.routing.Route
import com.campusmaps.routing.RouteCardText
import com.campusmaps.routing.RouteOption
import com.campusmaps.routing.RoutePlan
import com.campusmaps.ui.TripUiState
import com.campusmaps.ui.components.AppTopBar
import com.campusmaps.ui.components.AvoidStairsRow
import com.campusmaps.ui.components.IconTile
import com.campusmaps.ui.components.PrimaryPillButton
import com.campusmaps.ui.components.SmallTag
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.AppTextStyles
import com.campusmaps.ui.theme.Space

class RouteOptionsActions(
    val onBack: () -> Unit,
    val onReset: () -> Unit,
    val onSettings: () -> Unit,
    val onAvoidStairs: (Boolean) -> Unit,
    val onPickRoute: (RouteOption) -> Unit,
    val onAlreadyHere: (Route) -> Unit,
    val onGlasses: () -> Unit,
    val onTitleLongPress: () -> Unit,
)

// S1b Route options (section 5 of the handoff).
@Composable
fun RouteOptionsScreen(state: TripUiState, actions: RouteOptionsActions) {
    val destination = state.destination ?: return
    val plan = state.plan
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AppTopBar(
            title = "Route options",
            onBack = actions.onBack,
            onReset = actions.onReset,
            onSettings = actions.onSettings,
            onTitleLongPress = if (state.settings.demoMode) null else actions.onTitleLongPress,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = Space.xl, end = Space.xl, top = Space.s, bottom = Space.l),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("To ${destination.name}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${Formats.floorLong(destination.floor)} · ${state.building.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(fromLabel(state.start), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.simulatedTimeLabel?.let { SimulatedTimeChip(it, Modifier.padding(top = 6.dp)) }
                }
            }

            if (plan is RoutePlan.Options) {
                plan.lockedNotice?.let { notice -> item(key = "locked") { LockedBanner(notice) } }
            }

            item(key = "avoid") {
                AvoidStairsRow(checked = state.settings.avoidStairs, onChange = actions.onAvoidStairs, height = 48.dp)
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
                            startsInside = state.startsInside,
                            startFloor = state.start.floor,
                            onClick = { actions.onPickRoute(option) },
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

        // Glasses button pinned at the bottom (only when there is a route to follow).
        if (plan is RoutePlan.Options) {
            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(start = Space.xl, end = Space.xl, top = Space.s, bottom = Space.xxl),
            ) {
                OutlinedButton(
                    onClick = actions.onGlasses,
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("glassesButton"),
                ) {
                    Icon(AppIcons.eyeglasses, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text(
                        "Guide me with glasses",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
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

// One route card. The whole card is the tap target and opens S2.
// The best card stands out by more than color: tag, 2 dp border, bigger ETA and elevation.
@Composable
private fun RouteCard(
    option: RouteOption,
    best: RouteOption,
    isBest: Boolean,
    startsInside: Boolean,
    startFloor: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val title = RouteCardText.title(option, startsInside)
    val subtitle = RouteCardText.subtitle(option, startsInside, startFloor)
    val eta = Formats.eta(option.etaSeconds)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (isBest) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (isBest) colors.onPrimaryContainer else colors.onSurface,
        border = if (isBest) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = if (isBest) 4.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("routeCard_${option.id}")
            .semantics { contentDescription = "$title, $subtitle, $eta" + if (isBest) ", fastest" else "" },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = if (isBest) 16.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (isBest) 10.dp else 8.dp),
        ) {
            Row(verticalAlignment = if (isBest) Alignment.Top else Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(
                    icon = AppIcons.forMethod(option.method),
                    container = if (isBest) colors.primary else colors.secondaryContainer,
                    content = if (isBest) colors.onPrimary else colors.secondary,
                    size = 44.dp,
                    corner = 14.dp,
                    iconSize = 24.dp,
                    contentDescription = RouteCardText.methodWords(option.method),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (isBest) 4.dp else 2.dp)) {
                    if (isBest || option.usesStudentShortcut) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isBest) SmallTag("Fastest", container = colors.primary, content = colors.onPrimary)
                            if (option.usesStudentShortcut) {
                                SmallTag("Student shortcut", container = colors.tertiaryContainer, content = colors.onTertiaryContainer, icon = AppIcons.addRoad)
                            }
                        }
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = if (isBest) 17.sp else 16.sp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isBest) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    )
                }
                Text(eta, style = if (isBest) AppTextStyles.etaBest else AppTextStyles.etaOther)
            }
            if (isBest) {
                Column {
                    Text(RouteCardText.bestSummary(option), style = MaterialTheme.typography.bodySmall)
                    RouteCardText.alsoVia(option)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            } else {
                Text(RouteCardText.difference(option, best), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

// Start equals destination: one card.
@Composable
private fun AlreadyHereCard(roomName: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        border = BorderStroke(2.dp, colors.primary),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(AppIcons.checkCircle, container = colors.primary, content = colors.onPrimary, size = 44.dp, corner = 14.dp, iconSize = 24.dp)
            Column {
                Text("You are already here", style = MaterialTheme.typography.titleMedium)
                Text(roomName, style = MaterialTheme.typography.bodySmall)
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
