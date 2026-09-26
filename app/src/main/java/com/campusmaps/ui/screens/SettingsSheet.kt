package com.campusmaps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.data.AppSettings
import com.campusmaps.data.model.Building
import com.campusmaps.platform.TtsStatus
import com.campusmaps.ui.components.SectionLabel
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.CampusPalettes
import com.campusmaps.ui.theme.LocalCampusPalette
import com.campusmaps.ui.theme.Space

class SettingsActions(
    val onSpeak: (Boolean) -> Unit,
    val onAvoidStairs: (Boolean) -> Unit,
    val onDemoMode: (Boolean) -> Unit,
    val onBuilding: (String) -> Unit,
    val onResetDemo: () -> Unit,
    val onDismiss: () -> Unit,
)

// Settings (section 10, redesign sheet). Team only. Tidy, no extra depth. Rows are 56 dp:
// icon, label, one line of help, and a switch (campus line colour when on, warm grey when off, white thumb).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: AppSettings, tts: TtsStatus, buildings: List<Building>, actions: SettingsActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.xxl)
                .testTag("settingsSheet"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Settings", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            SettingsSwitchRow(
                icon = AppIcons.volumeUp,
                label = "Speak instructions",
                supporting = tts.label,
                checked = settings.speakInstructions,
                onChange = actions.onSpeak,
            )
            SettingsSwitchRow(
                icon = AppIcons.accessible,
                label = "Avoid stairs",
                supporting = "Same as the route screen",
                checked = settings.avoidStairs,
                onChange = actions.onAvoidStairs,
            )
            // No "Watch haptics" row: the design has one, but the app has no such setting.
            SettingsSwitchRow(
                icon = Icons.Rounded.Slideshow,
                label = "Demo mode",
                supporting = "Hides debug, keeps screen on, demo destinations only",
                checked = settings.demoMode,
                onChange = actions.onDemoMode,
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel("Demo building")
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                val colors = MaterialTheme.colorScheme
                buildings.forEachIndexed { index, building ->
                    SegmentedButton(
                        selected = building.id == settings.buildingId,
                        onClick = { actions.onBuilding(building.id) },
                        shape = SegmentedButtonDefaults.itemShape(index, buildings.size),
                        modifier = Modifier.heightIn(min = 52.dp),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = colors.primaryContainer,
                            activeContentColor = colors.onPrimaryContainer,
                            activeBorderColor = colors.outline,
                            inactiveContainerColor = Color.Transparent,
                            inactiveBorderColor = colors.outline,
                        ),
                        label = { Text(building.name, fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center, maxLines = 2) },
                    )
                }
            }

            TextButton(onClick = actions.onResetDemo, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(AppIcons.restartAlt, contentDescription = null)
                Text("Reset demo", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }

            if (!settings.demoMode) {
                Text(
                    "Long press the title to toggle the debug overlay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// One settings row. The whole row is the tap target (the Switch has no handler of its own).
@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    label: String,
    supporting: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // The sheet is dark over S2 / S3 / the preview (CampusMapsApp): the campus colours only apply to the light
    // campus scheme, whose surface is the palette's surface.
    val campusLight = scheme.surface == LocalCampusPalette.current.surface
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, color = scheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(supporting, color = scheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
        }
        // Empty thumbContent keeps the thumb 24 dp in both states, like the design.
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = {},
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = if (campusLight) scheme.secondary else scheme.primaryContainer,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = if (campusLight) CampusPalettes.switchOff else scheme.surfaceContainerHighest,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
