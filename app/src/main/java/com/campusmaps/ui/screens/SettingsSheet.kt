package com.campusmaps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.data.AppSettings
import com.campusmaps.data.model.Building
import com.campusmaps.platform.TtsStatus
import com.campusmaps.ui.components.AvoidStairsRow
import com.campusmaps.ui.components.SectionLabel
import com.campusmaps.ui.components.SwitchRow
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.Space

class SettingsActions(
    val onSpeak: (Boolean) -> Unit,
    val onAvoidStairs: (Boolean) -> Unit,
    val onDemoMode: (Boolean) -> Unit,
    val onBuilding: (String) -> Unit,
    val onResetDemo: () -> Unit,
    val onDismiss: () -> Unit,
)

// Settings (section 10). Team only. Tidy, no extra depth. Rows are 56 dp.
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
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            SwitchRow(
                label = "Speak instructions",
                supporting = tts.label,
                icon = AppIcons.volumeUp,
                checked = settings.speakInstructions,
                onChange = actions.onSpeak,
            )
            AvoidStairsRow(checked = settings.avoidStairs, onChange = actions.onAvoidStairs)
            SwitchRow(
                label = "Demo mode",
                supporting = "Hides debug, keeps screen on, demo destinations only",
                icon = AppIcons.schedule,
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
