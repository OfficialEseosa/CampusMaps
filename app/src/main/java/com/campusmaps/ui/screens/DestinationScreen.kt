package com.campusmaps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.GraphNode
import com.campusmaps.route.Formats
import com.campusmaps.ui.TripUiState
import com.campusmaps.ui.components.AppTopBar
import com.campusmaps.ui.components.AvoidStairsRow
import com.campusmaps.ui.components.IconTile
import com.campusmaps.ui.components.NavigationRow
import com.campusmaps.ui.components.PrimaryPillButton
import com.campusmaps.ui.components.SectionLabel
import com.campusmaps.ui.icons.AppIcons
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
)

// S1 Destination (section 4 of the handoff).
@Composable
fun DestinationScreen(state: TripUiState, buildings: List<Building>, actions: DestinationActions) {
    val demo = state.settings.demoMode
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding(),
    ) {
        AppTopBar(
            title = "CampusMaps",
            tags = listOfNotNull(state.building.code, if (demo) "DEMO" else null),
            onReset = actions.onReset,
            onSettings = actions.onSettings,
            onTitleLongPress = if (demo) null else actions.onTitleLongPress,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl)
                .padding(top = Space.s, bottom = Space.l),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Where to?", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)

            BuildingSelector(buildings, state.building.id, actions.onBuilding)

            // Search is hidden in demo mode; the layout still looks complete without it.
            if (!demo) {
                SearchField(state.query, actions.onQuery, actions.onSearch)
            }

            val searching = !demo && state.query.isNotBlank()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(
                    when {
                        searching -> "Search results"
                        demo -> "Demo destinations"
                        else -> "Recent and demo destinations"
                    },
                )
                val rows = if (searching) state.searchHits else state.destinationRows
                if (rows.isEmpty()) {
                    Text(
                        if (searching) "No rooms match \"${state.query.trim()}\"" else "None in this building file",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    rows.forEach { room ->
                        DestinationRow(
                            room = room,
                            buildingName = state.building.name,
                            selected = room.id == state.destination?.id,
                            onClick = { actions.onDestination(room.id) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Where are you?")
                StartPicker(state.start, state.startOptions, actions.onStart)
            }

            AvoidStairsRow(checked = state.settings.avoidStairs, onChange = actions.onAvoidStairs)

            if (!demo) {
                NavigationRow(icon = AppIcons.addRoad, label = "Found a faster way? Add a shortcut", onClick = actions.onAddShortcut)
                actions.onExplore?.let { NavigationRow(icon = AppIcons.locationOn, label = "Coming from across campus? See the map", onClick = it) }
            }

            state.routeError?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("routeError"))
            }
        }

        // Primary button pinned to the bottom with 28 dp bottom padding.
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(start = Space.xl, end = Space.xl, top = Space.s, bottom = Space.xxl),
        ) {
            val destination = state.destination
            PrimaryPillButton(
                text = if (destination != null) "Route to ${destination.name}" else "Pick a destination",
                enabled = destination != null,
                onClick = actions.onRoute,
                modifier = Modifier.testTag("routeButton"),
            )
        }
    }
}

// Three segments, full names, 52 dp tall, check icon on the selected one.
@Composable
private fun BuildingSelector(buildings: List<Building>, selectedId: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        buildings.forEachIndexed { index, building ->
            SegmentedButton(
                selected = building.id == selectedId,
                onClick = { onSelect(building.id) },
                shape = SegmentedButtonDefaults.itemShape(index, buildings.size),
                modifier = Modifier.heightIn(min = 52.dp),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primaryContainer,
                    activeContentColor = colors.onPrimaryContainer,
                    activeBorderColor = colors.outline,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = colors.onSurface,
                    inactiveBorderColor = colors.outline,
                ),
                label = {
                    Text(
                        building.name,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }
    }
}

// 56 dp, fully rounded, no border, search icon on the left.
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit) {
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text("Search room number or name", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(AppIcons.search, contentDescription = null) },
        // Clear button (docs/20 QA #13, lost in the teammate's field; docs/22 #3).
        trailingIcon = if (query.isNotEmpty()) {
            {
                androidx.compose.material3.IconButton(onClick = { onQuery("") }) {
                    Icon(AppIcons.close, contentDescription = "Clear search")
                }
            }
        } else null,
        shape = RoundedCornerShape(28.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSearch()
            focus.clearFocus()
        }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = colors.onSurfaceVariant,
            unfocusedLeadingIconColor = colors.onSurfaceVariant,
            focusedTrailingIconColor = colors.onSurfaceVariant,
            unfocusedTrailingIconColor = colors.onSurfaceVariant,
            focusedPlaceholderColor = colors.onSurfaceVariant,
            unfocusedPlaceholderColor = colors.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("search"),
    )
}

// One destination card. Selected: primaryContainer, 2 dp primary border, white icon on primary.
@Composable
fun DestinationRow(room: GraphNode, buildingName: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("destination_${room.id}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(
                icon = AppIcons.doorFront,
                container = if (selected) colors.primary else colors.secondaryContainer,
                content = if (selected) colors.onPrimary else colors.secondary,
                size = 40.dp,
                iconSize = 20.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Formats.floorLong(room.floor)} · $buildingName",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
            if (selected) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(AppIcons.check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Selected", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// "Where are you?" row: 56 dp outlined, location_on in primary, dropdown chevron.
// Later this shows the live position from sign recognition.
@Composable
private fun StartPicker(start: GraphNode, options: List<GraphNode>, onStart: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            border = BorderStroke(1.dp, colors.outline),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("startPicker"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(AppIcons.locationOn, contentDescription = null, tint = colors.primary)
                Text(startLabel(start), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
                Icon(AppIcons.dropdown, contentDescription = "Change start", tint = colors.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(startLabel(option), style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(AppIcons.locationOn, contentDescription = null, tint = colors.primary) },
                    onClick = {
                        onStart(option.id)
                        open = false
                    },
                )
            }
        }
    }
}

fun startLabel(node: GraphNode): String =
    if (node.isOutdoor) "Outside: ${node.name}" else "Inside: ${node.name}, ${Formats.floorLong(node.floor)}"

// "From: Outside, P1 Decatur St side" on S1b.
fun fromLabel(node: GraphNode): String =
    if (node.isOutdoor) "From: Outside, ${node.name}" else "From: ${node.name}, ${Formats.floorLong(node.floor)}"
