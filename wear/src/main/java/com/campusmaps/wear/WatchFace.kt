package com.campusmaps.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.shared.WatchStep
import com.campusmaps.shared.WatchStepType

// Colors shared with the phone's ArOverlayColors (kept in sync by hand; the watch has no theme).
private object WatchColors {
    val arrowCore = Color(0xFFFFB27A)
    val attention = Color(0xFFFFC247)
    val arrived = Color(0xFF7BC98A)
    val muted = Color(0xFFCFD8DC) // glassesMuted
}

private val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_bold, FontWeight.Bold),
    Font(R.font.sora_extrabold, FontWeight.ExtraBold),
)

// The whole watch UI (section 12): pure black, one big arrow, one short fact, one small label.
// Stays inside the round safe area. Readable at arm's length in under half a second.
@Composable
fun WatchFace(step: WatchStep?, ambient: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (step == null) {
            BasicText(
                "Start a route on your phone",
                style = TextStyle(color = WatchColors.muted, fontFamily = Sora, fontSize = 14.sp, textAlign = TextAlign.Center),
            )
            return@Box
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.semantics { contentDescription = "${step.label}, ${step.bigText}" },
        ) {
            StepArrow(step.type, ambient, Modifier.size(104.dp))
            BasicText(
                step.bigText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            BasicText(
                step.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = WatchColors.muted, fontFamily = Sora, fontSize = 14.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// Big arrow for each step type. Interactive: filled in color. Ambient: 2 dp white outline only.
@Composable
private fun StepArrow(type: WatchStepType, ambient: Boolean, modifier: Modifier) {
    val color = when (type) {
        WatchStepType.LOCKED -> WatchColors.attention
        WatchStepType.ARRIVED -> WatchColors.arrived
        else -> WatchColors.arrowCore
    }
    Canvas(modifier) {
        val s = size.minDimension / 100f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun polygon(vararg xy: Float): Path = Path().apply {
            moveTo(xy[0] * s, xy[1] * s)
            var i = 2
            while (i < xy.size) { lineTo(xy[i] * s, xy[i + 1] * s); i += 2 }
            close()
        }
        val paint = if (ambient) Color.White else color
        val style = if (ambient) Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round) else Fill
        val thick = Stroke(width = if (ambient) 2.dp.toPx() else 10f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (type) {
            WatchStepType.STRAIGHT -> drawPath(polygon(40f, 92f, 40f, 42f, 20f, 42f, 50f, 8f, 80f, 42f, 60f, 42f, 60f, 92f), paint, style = style)
            WatchStepType.LEFT -> drawPath(polygon(52f, 92f, 52f, 53f, 34f, 53f, 34f, 70f, 8f, 45f, 34f, 20f, 34f, 37f, 68f, 37f, 68f, 92f), paint, style = style)
            WatchStepType.RIGHT -> drawPath(polygon(48f, 92f, 48f, 53f, 66f, 53f, 66f, 70f, 92f, 45f, 66f, 20f, 66f, 37f, 32f, 37f, 32f, 92f), paint, style = style)
            WatchStepType.STAIRS -> drawPath(polygon(8f, 92f, 8f, 72f, 29f, 72f, 29f, 51f, 50f, 51f, 50f, 30f, 71f, 30f, 71f, 9f, 92f, 9f, 92f, 92f), paint, style = style)
            // The STAIRS shape mirrored horizontally: steps descend from left to right.
            WatchStepType.STAIRS_DOWN -> drawPath(polygon(92f, 92f, 92f, 72f, 71f, 72f, 71f, 51f, 50f, 51f, 50f, 30f, 29f, 30f, 29f, 9f, 8f, 9f, 8f, 92f), paint, style = style)
            WatchStepType.ELEVATOR -> drawElevator(s, paint, ambient)
            WatchStepType.DOOR -> {
                drawRoundRect(paint, topLeft = p(26f, 8f), size = Size(48f * s, 84f * s), cornerRadius = CornerRadius(4f * s), style = style)
                if (!ambient) drawCircle(Color.Black, radius = 4.5f * s, center = p(63f, 52f))
            }
            WatchStepType.LOCKED -> {
                val shackle = Path().apply {
                    moveTo(32f * s, 46f * s)
                    lineTo(32f * s, 32f * s)
                    cubicTo(32f * s, 8f * s, 68f * s, 8f * s, 68f * s, 32f * s)
                    lineTo(68f * s, 46f * s)
                }
                drawPath(shackle, paint, style = thick)
                drawRoundRect(paint, topLeft = p(18f, 44f), size = Size(64f * s, 48f * s), cornerRadius = CornerRadius(8f * s), style = style)
            }
            WatchStepType.ARRIVED -> {
                val check = Path().apply {
                    moveTo(14f * s, 52f * s)
                    lineTo(40f * s, 76f * s)
                    lineTo(86f * s, 26f * s)
                }
                drawPath(check, paint, style = if (ambient) thick else Stroke(width = 14f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// Elevator: a car with an up and a down triangle (differs from stairs in shape, not just color).
private fun DrawScope.drawElevator(s: Float, paint: Color, ambient: Boolean) {
    val outline = Stroke(width = if (ambient) 2.dp.toPx() else 7f * s, join = StrokeJoin.Round)
    drawRoundRect(paint, topLeft = Offset(16f * s, 6f * s), size = Size(68f * s, 88f * s), cornerRadius = CornerRadius(8f * s), style = outline)
    val up = Path().apply { moveTo(50f * s, 18f * s); lineTo(66f * s, 42f * s); lineTo(34f * s, 42f * s); close() }
    val down = Path().apply { moveTo(50f * s, 82f * s); lineTo(66f * s, 58f * s); lineTo(34f * s, 58f * s); close() }
    val fill = if (ambient) Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round) else Fill
    drawPath(up, paint, style = fill)
    drawPath(down, paint, style = fill)
}

@Preview(widthDp = 227, heightDp = 227, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewLeft() = WatchFace(WatchStep(WatchStepType.LEFT, "10 m", "Atrium north"), ambient = false)

@Preview(widthDp = 227, heightDp = 227, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAmbient() = WatchFace(WatchStep(WatchStepType.ELEVATOR, "Floor 6", "Elevator"), ambient = true)
