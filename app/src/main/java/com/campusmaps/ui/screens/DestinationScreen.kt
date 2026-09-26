package com.campusmaps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.data.campus.Campus
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.GraphNode
import com.campusmaps.route.Formats
import com.campusmaps.ui.TripUiState
import com.campusmaps.ui.components.NavigationRow
import com.campusmaps.ui.components.SmallTag
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.CampusPalettes
import com.campusmaps.ui.theme.LocalCampusPalette
import com.campusmaps.ui.theme.Space

// Callbacks S1 needs. Grouped so the screen signature stays readable.
class DestinationActions(
    val onBuilding: (String) -> Unit,
    val onQuery: (String) -> Unit,
    val onSearch: () -> Unit,
    val onDestination: (String) -> Unit,
    val onStart: (String) -> Unit,
    val onAvoidStairs: (Boolean) -> Unit,
    val onAddShortcut: () -> Unit,
    val onRoute: () -> Unit,
    val onReset: () -> Unit,
    val onSettings: () -> Unit,
    val onTitleLongPress: () -> Unit,
    val onExplore: (() -> Unit)? = null, // "See the campus map" (Explore, leg 1); null hides the row
    val onBuildings: (() -> Unit)? = null, // Campus pill opens the building list (S0b); null makes it a plain label
)

// S1 Destination (redesign screen "03 Where to"). [buildings] are the mapped buildings of [campus] only.
@Composable
fun DestinationScreen(state: TripUiState, campus: Campus, buildings: List<Building>, actions: DestinationActions) {
    val demo = state.settings.demoMode
    val palette = LocalCampusPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.surface)
            .imePadding(),
    ) {
        DestinationTopBar(
            code = state.building.code,
            demo = demo,
            onReset = actions.onReset,
            onSettings = actions.onSettings,
            onTitleLongPress = if (demo) null else actions.onTitleLongPress,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl)
                .padding(top = Space.xs, bottom = Space.l),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CampusPill("${campus.name} · ${state.building.name}", actions.onBuildings)

            Text(
                "Where to?",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-1).sp),
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink,
            )

            // One mapped building on this campus (Georgia Tech today): nothing to choose, so no selector.
            if (buildings.size > 1) {
                BuildingSelector(buildings, state.building.id, actions.onBuilding)
            }

            // Search is hidden in demo mode; the layout still looks complete without it.
            if (!demo) {
                SearchField(state.query, actions.onQuery, actions.onSearch)
            }

            val searching = !demo && state.query.isNotBlank()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DesignLabel(
                    when {
                        searching -> "Search results"
                        demo -> "Demo destinations"
                        else -> "Destinations in ${state.building.name}"
                    },
                )
                val rows = if (searching) state.searchHits else state.destinationRows
                if (rows.isEmpty()) {
                    Text(
                        if (searching) "No rooms match \"${state.query.trim()}\"" else "None in this building file",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                    )
                }
                rows.forEach { room ->
                    DestinationRow(
                        room = room,
                        buildingName = state.building.name,
                        selected = room.id == state.destination?.id,
                        onClick = { actions.onDestination(room.id) },
                    )
                }
            }

            DesignLabel("Where are you?")
            StartPicker(state.start, state.startOptions, actions.onStart)

            AvoidStairsSwitchRow(checked = state.settings.avoidStairs, onChange = actions.onAvoidStairs)

            if (!demo) {
                NavigationRow(icon = AppIcons.addRoad, label = "Found a faster way? Add a shortcut", onClick = actions.onAddShortcut)
                actions.onExplore?.let { NavigationRow(icon = AppIcons.locationOn, label = "Coming from across campus? See the map", onClick = it) }
            }

            state.routeError?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("routeError"))
            }
        }

        // Primary button pinned to the bottom: 10 dp above, 30 dp below.
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(start = Space.xl, end = Space.xl, top = 10.dp, bottom = 30.dp),
        ) {
            RouteButton(destinationName = state.destination?.name, onClick = actions.onRoute)
        }
    }
}

// 56 dp bar: "CampusMaps", building code tag on the soft colour (+ DEMO), quiet reset and settings.
@Composable
private fun DestinationTopBar(
    code: String,
    demo: Boolean,
    onReset: () -> Unit,
    onSettings: () -> Unit,
    onTitleLongPress: (() -> Unit)?,
) {
    val palette = LocalCampusPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    // The only hidden gesture in the app: long press toggles the debug overlay.
                    if (onTitleLongPress != null) {
                        Modifier.pointerInput(onTitleLongPress) { detectTapGestures(onLongPress = { onTitleLongPress() }) }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "CampusMaps",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
                maxLines = 1,
            )
            Text(
                code,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.soft)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            if (demo) {
                SmallTag(
                    text = "DEMO",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        IconButton(onClick = onReset, modifier = Modifier.size(44.dp)) {
            Icon(AppIcons.restartAlt, contentDescription = "Reset", tint = palette.muted, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)) {
            Icon(AppIcons.settings, contentDescription = "Settings", tint = palette.muted, modifier = Modifier.size(22.dp))
        }
    }
}

// Accent pill "Campus · Building" that opens the building list (S0b).
@Composable
private fun CampusPill(text: String, onClick: (() -> Unit)?) {
    val palette = LocalCampusPalette.current
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(palette.accent)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 10.dp, end = 12.dp)
            .testTag("campusPill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.LocationCity, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(17.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = palette.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Icon(AppIcons.dropdown, contentDescription = "Change building", tint = palette.onAccent, modifier = Modifier.size(17.dp))
        }
    }
}

// Section labels: 12 sp bold, muted, slight tracking.
@Composable
private fun DesignLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
        color = LocalCampusPalette.current.muted,
    )
}

// Pill track (card, 1 dp border, 4 dp padding); the selected segment sits on the surface with a small shadow.
@Composable
private fun BuildingSelector(buildings: List<Building>, selectedId: String, onSelect: (String) -> Unit) {
    val palette = LocalCampusPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(palette.card)
            .border(1.dp, palette.cardBorder, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        buildings.forEach { building ->
            val selected = building.id == selectedId
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .then(if (selected) Modifier.shadow(2.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (selected) palette.surface else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(building.id) })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    building.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) palette.ink else palette.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

// 52 dp card pill, search icon on the left, clear button on the right while there is text.
// The value is the synchronous search text from the view model (docs/22 #2).
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit) {
    val focus = LocalFocusManager.current
    val palette = LocalCampusPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(palette.card)
            .padding(start = 18.dp, end = if (query.isNotEmpty()) 6.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.search, contentDescription = null, tint = palette.muted, modifier = Modifier.size(22.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.ink),
            cursorBrush = SolidColor(palette.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch()
                focus.clearFocus()
            }),
            modifier = Modifier
                .weight(1f)
                .testTag("search"),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Search room number or name",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.muted.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
        // Clear button (docs/20 QA #13, lost in the teammate's field; docs/22 #3).
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }, modifier = Modifier.size(40.dp)) {
                Icon(AppIcons.close, contentDescription = "Clear search", tint = palette.muted)
            }
        }
    }
}

// One destination card. Selected: soft fill, 2 dp line border, white icon on a line tile, check_circle.
@Composable
fun DestinationRow(room: GraphNode, buildingName: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalCampusPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) palette.soft else palette.card,
        contentColor = palette.ink,
        border = if (selected) BorderStroke(2.dp, palette.line) else BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("destination_${room.id}"),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) palette.line else palette.cardBorder),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.doorFront, contentDescription = null, tint = if (selected) Color.White else palette.ink, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                Text(
                    "${Formats.floorLong(room.floor)} · $buildingName",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = palette.muted,
                )
            }
            if (selected) {
                Icon(AppIcons.checkCircle, contentDescription = "Selected", tint = palette.line, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// "Where are you?" row: 54 dp, 1.5 dp card border, my_location in the line colour, dropdown chevron.
// Later this shows the live position from sign recognition.
@Composable
private fun StartPicker(start: GraphNode, options: List<GraphNode>, onStart: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val palette = LocalCampusPalette.current
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, palette.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag("startPicker"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.myLocation, contentDescription = null, tint = palette.line, modifier = Modifier.size(22.dp))
                Text(
                    startLabel(start),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink,
                    modifier = Modifier.weight(1f),
                )
                Icon(AppIcons.dropdown, contentDescription = "Change start", tint = palette.muted, modifier = Modifier.size(22.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(startLabel(option), style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(AppIcons.myLocation, contentDescription = null, tint = palette.line) },
                    onClick = {
                        onStart(option.id)
                        open = false
                    },
                )
            }
        }
    }
}

// "Avoid stairs": 54 dp row, the whole row is the tap target. Track in the line colour when on,
// warm grey when off, white thumb, no outline.
@Composable
private fun AvoidStairsSwitchRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalCampusPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AppIcons.accessible, contentDescription = null, tint = palette.ink, modifier = Modifier.size(22.dp))
        Text(
            "Avoid stairs",
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
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

// 58 dp pill. Enabled: accent "Route to X  ->"; disabled: card border fill, muted "Pick a destination".
@Composable
private fun RouteButton(destinationName: String?, onClick: () -> Unit) {
    val palette = LocalCampusPalette.current
    Button(
        onClick = onClick,
        enabled = destinationName != null,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.accent,
            contentColor = palette.onAccent,
            disabledContainerColor = palette.cardBorder,
            disabledContentColor = palette.muted,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("routeButton"),
    ) {
        Text(
            if (destinationName != null) "Route to $destinationName" else "Pick a destination",
            style = MaterialTheme.typography.labelLarge,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.size(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

fun startLabel(node: GraphNode): String =
    if (node.isOutdoor) "Outside: ${node.name}" else "Inside: ${node.name}, ${Formats.floorLong(node.floor)}"

// "From: Outside, P1 Decatur St side" on S1b.
fun fromLabel(node: GraphNode): String =
    if (node.isOutdoor) "From: Outside, ${node.name}" else "From: ${node.name}, ${Formats.floorLong(node.floor)}"
