package com.campusmaps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.campusmaps.guidance.GuidanceState
import com.campusmaps.ui.theme.AppTextStyles
import com.campusmaps.ui.theme.ArOverlayColors
import kotlin.math.roundToInt

// One tappable action in the debug card.
data class DebugLink(val label: String, val onClick: () -> Unit)

// Debug overlay (section 11): draggable, 95% opaque so screen text does not bleed through,
// collapses to one line. Hidden completely in demo mode (the caller never shows it then).
@Composable
fun DebugOverlay(
    guidance: GuidanceState?,
    lines: List<String>,
    links: List<DebugLink>,
    onClose: () -> Unit,
) {
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }
    var collapsed by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .statusBarsPadding()
            .padding(top = 120.dp, start = 12.dp)
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .width(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ArOverlayColors.debugCard)
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); offset += drag } }
            .padding(12.dp)
            .testTag("debugOverlay"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DEBUG", style = AppTextStyles.debugMono, color = ArOverlayColors.debugHeader, fontWeight = FontWeight.Bold)
            if (collapsed) {
                val summary = guidance?.let { "step ${it.progress.stepIndex + 1}/${it.route.steps.size} · conf ${"%.2f".format(it.pose.confidence)}" } ?: "no route"
                Text(summary, style = AppTextStyles.debugMono, color = ArOverlayColors.text, maxLines = 1, modifier = Modifier.weight(1f))
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            LinkText(if (collapsed) "open" else "fold") { collapsed = !collapsed }
            LinkText("close", onClose)
        }
        if (!collapsed) {
            lines.forEach { Text(it, style = AppTextStyles.debugMono, color = ArOverlayColors.text) }
            links.forEach { LinkText(it.label, it.onClick) }
        }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = AppTextStyles.debugMono,
        color = ArOverlayColors.debugLink,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
