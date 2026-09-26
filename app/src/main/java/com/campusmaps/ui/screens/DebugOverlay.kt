package com.campusmaps.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.data.AppClock
import com.campusmaps.data.ClockMode
import com.campusmaps.data.model.GraphNode
import com.campusmaps.guidance.GuidanceState
import com.campusmaps.ui.WalkState
import com.campusmaps.ui.theme.AppTextStyles
import com.campusmaps.ui.theme.ArOverlayColors
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.math.roundToInt

// One tappable action in the debug card.
data class DebugLink(val label: String, val onClick: () -> Unit)

// Debug overlay (section 11 of the handoff, plus Raphael's controls from docs/20): 95% opaque so screen text
// does not bleed through, collapses to one line. The header row is the drag handle (a drag on the body would
// be eaten by its scroll) and the card is clamped on screen. Hidden completely in demo mode.
@Composable
fun DebugOverlay(
    guidance: GuidanceState?,
    lines: List<String>,
    links: List<DebugLink>,
    onClose: () -> Unit,
    controls: @Composable ColumnScope.() -> Unit = {},
) {
    var ox by rememberSaveable { mutableFloatStateOf(0f) }
    var oy by rememberSaveable { mutableFloatStateOf(0f) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val conf = LocalConfiguration.current
    val maxX = with(density) { (conf.screenWidthDp.dp - 120.dp).toPx() }
    val maxY = with(density) { (conf.screenHeightDp.dp - 200.dp).toPx() }

    Column(
        Modifier
            .statusBarsPadding()
            .padding(top = 120.dp, start = 12.dp)
            .offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            // Opaque: at 95% the S1 text behind read through the card (docs/22 #15).
            .background(ArOverlayColors.debugCard.copy(alpha = 1f))
            .padding(12.dp)
            .testTag("debugOverlay"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(min = 36.dp).pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    ox = (ox + drag.x).coerceIn(-maxX, maxX)
                    oy = (oy + drag.y).coerceIn(-300f, maxY)
                }
            },
        ) {
            Text("DEBUG", style = AppTextStyles.debugMono, color = ArOverlayColors.debugHeader, fontWeight = FontWeight.Bold)
            if (collapsed) {
                val summary = guidance?.let { "step ${it.progress.stepIndex + 1}/${it.route.steps.size} · conf ${"%.2f".format(it.pose.confidence)}" } ?: "no route"
                Text(summary, style = AppTextStyles.debugMono, color = ArOverlayColors.text, maxLines = 1, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            LinkText(if (collapsed) "open" else "fold") { collapsed = !collapsed }
            LinkText("close", onClose)
        }
        if (!collapsed) {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lines.forEach { Text(it, style = AppTextStyles.debugMono, color = ArOverlayColors.text) }
                controls()
                links.forEach { LinkText(it.label, it.onClick) }
            }
        }
    }
}

// Raphael's debug controls (docs/20): simulated-time switch and picker, fake walk, low confidence, jump picker.
@Composable
fun DebugControls(
    clock: ClockMode,
    walk: WalkState,
    guiding: Boolean,
    arrived: Boolean,
    jumpTargets: List<GraphNode>,
    jumpLabel: String,
    onSimulated: (Boolean) -> Unit,
    onSetTime: (DayOfWeek, LocalTime) -> Unit,
    onStep: () -> Unit,
    onWalk: () -> Unit,
    onInterval: (Int) -> Unit,
    onPause: () -> Unit,
    onLowConfidence: (Boolean) -> Unit,
    onJump: (String) -> Unit,
) {
    var timeDialog by remember { mutableStateOf(false) }
    val sim = clock as? ClockMode.Simulated
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SwitchRow("Sim time", sim != null, onSimulated)
        if (sim != null) {
            LinkText("${sim.day.name.take(3)} ${AppClock.eventDay(sim.day)} ${sim.time}  (change)") { timeDialog = true }
        }
        Text("Fake walk (every ${walk.intervalSec} s)", style = AppTextStyles.debugMono, color = ArOverlayColors.debugHeader)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallButton("Step", enabled = guiding && !arrived, onClick = onStep)
            SmallButton(if (walk.auto) "Pause" else "Walk", enabled = guiding && !arrived, onClick = onWalk)
            SmallButton("-") { onInterval(-1) }
            SmallButton("+") { onInterval(1) }
        }
        if (guiding) {
            LinkText(if (walk.paused) "Continuous walker: paused (tap: resume)" else "Continuous walker: on (tap: pause)", onPause)
        }
        SwitchRow("Low confidence", walk.lowConfidence, onLowConfidence)
        Text("Jump to node / start point:", style = AppTextStyles.debugMono, color = ArOverlayColors.text)
        JumpPicker(jumpTargets, jumpLabel, onJump)
    }
    if (timeDialog && sim != null) {
        SimTimeDialog(sim, onDismiss = { timeDialog = false }) { d, t -> onSetTime(d, t); timeDialog = false }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTextStyles.debugMono, color = ArOverlayColors.text, modifier = Modifier.weight(1f))
        Switch(checked, onChange, modifier = Modifier.testTag("debugSwitch_${label.replace(' ', '_')}"))
    }
}

@Composable
private fun JumpPicker(targets: List<GraphNode>, label: String, onJump: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        LinkText("$label  (tap to pick)") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            targets.forEach { n ->
                DropdownMenuItem(
                    text = { Text(if (n.isOutdoor) "Outside: ${n.id} ${n.name}" else "${n.id}  ${n.name} (F${n.floor})", fontSize = 13.sp) },
                    onClick = { onJump(n.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, fontSize = 13.sp, color = if (enabled) ArOverlayColors.debugLink else ArOverlayColors.textMuted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimTimeDialog(initial: ClockMode.Simulated, onDismiss: () -> Unit, onSet: (DayOfWeek, LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initial.time.hour, initial.time.minute, is24Hour = true)
    var day by remember { mutableStateOf(initial.day) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSet(day, LocalTime.of(picker.hour, picker.minute)) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Simulated time") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY).forEach { d ->
                        FilterChip(selected = d == day, onClick = { day = d },
                            label = { Text(d.name.take(3), maxLines = 1, softWrap = false, fontSize = 13.sp) })
                    }
                }
                TimePicker(picker)
            }
        },
    )
}

/** Latest TYPE_PRESSURE reading in hPa, or null when the device has no barometer (Raphael's debug line). */
@Composable
fun rememberPressure(): Float? {
    val ctx = LocalContext.current
    var value by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { value = e.values[0] }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return value
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = AppTextStyles.debugMono,
        color = ArOverlayColors.debugLink,
        // Exactly 36 dp, the row pitch, so neighbouring link rows no longer overlap their tap areas (docs/22 #15).
        modifier = Modifier
            .height(36.dp)
            .clickable(onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}
