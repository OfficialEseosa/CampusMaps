package com.campusmaps.ui.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.ui.theme.Sora
import kotlinx.coroutines.launch

/**
 * Wraps the app. The app content is composed at once underneath; the splash overlay sits on top
 * for about 1.4 s and then fades away. Nothing here runs before the first frame: the animation
 * clock starts after the first frame is drawn, and nothing is loaded.
 */
@Composable
fun SplashHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // "Remove animations" (animator duration scale 0): skip straight to the end state, the app.
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // Saveable so a configuration change (dark mode toggle) does not replay it.
    var showing by rememberSaveable { mutableStateOf(SHOW_SPLASH && !animationsOff) }
    Box(Modifier.fillMaxSize()) {
        content()
        if (showing) SplashOverlay(onFinished = { showing = false })
    }
}

/** The animated splash. Calls [onFinished] once it has fully faded out (or been skipped). */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val clock = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val total = SplashTiming.TOTAL * SplashTiming.SCALE

    LaunchedEffect(Unit) {
        withFrameNanos { } // Start after the first frame is on screen.
        clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
        onFinished()
    }

    SplashFrame(
        timeMs = { clock.value / SplashTiming.SCALE },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures {
                // Skip: jump to the crossfade. This cancels the running animation above.
                scope.launch {
                    val fadeStart = SplashTiming.FADE_START * SplashTiming.SCALE
                    if (clock.value < fadeStart) clock.snapTo(fadeStart)
                    val left = (total - clock.value).toInt().coerceAtLeast(1)
                    clock.animateTo(total, tween(left, easing = LinearEasing))
                    onFinished()
                }
            }
        },
    )
}

/**
 * The splash at a point in time on the unscaled SplashTiming clock. [timeMs] is read only in the
 * draw and layer phases, so the animation never recomposes (60 fps friendly).
 */
@Composable
fun SplashFrame(timeMs: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - phase(timeMs(), SplashTiming.FADE_START, SplashTiming.TOTAL) }
            .clearAndSetSemantics { testTag = "splash" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Ink with the Clay tint on top (the system splash uses the same colour pre-mixed).
            drawRect(SplashColors.Ink)
            drawRect(SplashColors.Clay, alpha = SplashColors.CLAY_OVERLAY_ALPHA)
            // Small logo mark matching the system splash icon, fading out as the frame draws in.
            val handoff = 1f - phase(timeMs(), SplashTiming.HANDOFF_START, SplashTiming.HANDOFF_END)
            if (handoff > 0f) {
                val markPx = SplashTiming.HANDOFF_MARK_DP.dp.toPx() * (1f + 0.15f * (1f - handoff))
                drawLogoMark(center, markPx, alpha = handoff)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier
                    .fillMaxWidth(0.72f)
                    .widthIn(max = 320.dp)
                    .aspectRatio(FRAME_W / FRAME_H),
            ) { drawRouteArt(timeMs()) }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "CampusMaps",
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.4).sp),
                color = SplashColors.Mark,
                modifier = Modifier.graphicsLayer {
                    val word = phase(timeMs(), SplashTiming.WORD_START, SplashTiming.WORD_END)
                    alpha = word
                    translationY = (1f - easeOut(word)) * 12.dp.toPx()
                },
            )
        }
    }
}

// ---- Drawing ---------------------------------------------------------------------------------

// Design units: the viewfinder frame of the "01 Welcome" board (390 x 844), moved to (0, 0).
private const val FRAME_W = 310f
private const val FRAME_H = 302f

private class Chevron(val left: Offset, val tip: Offset, val right: Offset, val width: Float)

// Bottom to top, as on the Welcome board.
private val CHEVRONS = listOf(
    Chevron(Offset(110f, 284f), Offset(155f, 264f), Offset(200f, 284f), 9f),
    Chevron(Offset(122f, 234f), Offset(155f, 219f), Offset(188f, 234f), 7f),
    Chevron(Offset(132f, 194f), Offset(155f, 183f), Offset(178f, 194f), 5.5f),
    Chevron(Offset(140f, 162f), Offset(155f, 155f), Offset(170f, 162f), 4f),
)

private const val PIN_SCALE = 3.2f // logo units (40 box) to design units
private val PIN_TIP = Offset(155f, 90f)
private val DOT = Offset(155f, 144f)

/** The four viewfinder corners, each its own contour so they trim together. */
private fun cornerPaths(w: Float, h: Float, r: Float, arm: Float): List<Path> = listOf(
    Path().apply { moveTo(0f, arm); lineTo(0f, r); arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false); lineTo(arm, 0f) },
    Path().apply { moveTo(w - arm, 0f); lineTo(w - r, 0f); arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false); lineTo(w, arm) },
    Path().apply { moveTo(w, h - arm); lineTo(w, h - r); arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false); lineTo(w - arm, h) },
    Path().apply { moveTo(arm, h); lineTo(r, h); arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false); lineTo(0f, h - arm) },
)

/** The logo's map pin in its 40-unit box (tip at 20,30; head centre 20,18, radius 7). */
private fun pinPath(): Path = Path().apply {
    moveTo(20f, 30f)
    cubicTo(20f, 30f, 13f, 23.8f, 13f, 18f)
    arcTo(Rect(13f, 11f, 27f, 25f), 180f, 180f, false)
    cubicTo(27f, 23.8f, 20f, 30f, 20f, 30f)
    close()
}

private val FRAME_CORNERS = cornerPaths(FRAME_W, FRAME_H, 12f, 46f)
private val LOGO_CORNERS = cornerPaths(32f, 32f, 2f, 8f) // the logo's corners, inset by 4
private val PIN = pinPath()
private val MARK_STROKE = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val FRAME_STROKE = Stroke(width = 3.5f, cap = StrokeCap.Round)
private val DASH = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))

private fun DrawScope.drawRouteArt(t: Float) {
    val s = size.width / FRAME_W
    withTransform({ scale(s, s, Offset.Zero) }) {
        // Corners draw in.
        val corners = easeInOut(phase(t, SplashTiming.CORNERS_START, SplashTiming.CORNERS_END))
        if (corners >= 1f) {
            FRAME_CORNERS.forEach { drawPath(it, SplashColors.Mark, style = FRAME_STROKE) }
        } else if (corners > 0f) {
            val measure = PathMeasure()
            for (p in FRAME_CORNERS) {
                measure.setPath(p, false)
                val seg = Path()
                measure.getSegment(0f, measure.length * corners, seg, true)
                drawPath(seg, SplashColors.Mark, style = FRAME_STROKE)
            }
        }

        // Chevrons fade in bottom to top, each rising a little.
        CHEVRONS.forEachIndexed { i, c ->
            val start = SplashTiming.CHEVRON_START + i * SplashTiming.CHEVRON_STAGGER
            val a = phase(t, start, start + SplashTiming.CHEVRON_FADE)
            if (a > 0f) {
                val dy = (1f - easeOut(a)) * 10f
                val path = Path().apply {
                    moveTo(c.left.x, c.left.y + dy); lineTo(c.tip.x, c.tip.y + dy); lineTo(c.right.x, c.right.y + dy)
                }
                drawPath(path, SplashColors.Mark, alpha = 0.95f * a, style = Stroke(c.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Dot, then the dashed line up to the pin.
        val line = phase(t, SplashTiming.LINE_START, SplashTiming.LINE_END)
        if (line > 0f) {
            drawCircle(SplashColors.Mark, radius = 5f * easeOutBack(line).coerceAtLeast(0f), center = DOT)
            drawLine(
                SplashColors.Mark, Offset(DOT.x, DOT.y - 4f), Offset(DOT.x, DOT.y - 4f - 46f * easeOut(line)),
                strokeWidth = 2f, pathEffect = DASH,
            )
        }

        // Pin drops in with a small overshoot.
        val pin = phase(t, SplashTiming.PIN_START, SplashTiming.PIN_END)
        if (pin > 0f) {
            val drop = (1f - easeOutBack(pin)) * -70f
            val alpha = phase(t, SplashTiming.PIN_START, SplashTiming.PIN_START + 120f)
            translate(PIN_TIP.x - 20f * PIN_SCALE, PIN_TIP.y - 30f * PIN_SCALE + drop) {
                withTransform({ scale(PIN_SCALE, PIN_SCALE, Offset.Zero) }) {
                    drawPath(PIN, SplashColors.Mark, alpha = alpha, style = MARK_STROKE)
                    drawCircle(SplashColors.Mark, radius = 2.5f, center = Offset(20f, 18f), alpha = alpha, style = MARK_STROKE)
                }
            }
        }
    }
}

/** The 40-unit logo mark (corners and pin), centred at [center], [sizePx] wide. */
private fun DrawScope.drawLogoMark(center: Offset, sizePx: Float, alpha: Float) {
    val k = sizePx / 40f
    translate(center.x - sizePx / 2f, center.y - sizePx / 2f) {
        withTransform({ scale(k, k, Offset.Zero) }) {
            translate(4f, 4f) { LOGO_CORNERS.forEach { drawPath(it, SplashColors.Mark, alpha = alpha, style = MARK_STROKE) } }
            drawPath(PIN, SplashColors.Mark, alpha = alpha, style = MARK_STROKE)
            drawCircle(SplashColors.Mark, radius = 2.5f, center = Offset(20f, 18f), alpha = alpha, style = MARK_STROKE)
        }
    }
}

// ---- Easing ----------------------------------------------------------------------------------

/** 0 before [start], 1 after [end], linear in between. */
private fun phase(t: Float, start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

private fun easeInOut(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

/** Overshoots by about 10 % and settles: the pin's small bounce. */
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val y = x - 1f
    return 1f + c3 * y * y * y + c1 * y * y
}

// ---- Previews --------------------------------------------------------------------------------

@Preview(name = "Splash, first frame (handoff)", widthDp = 390, heightDp = 844)
@Composable
private fun SplashFirstFramePreview() = SplashFrame(timeMs = { 0f })

@Preview(name = "Splash, mid (pin dropping)", widthDp = 390, heightDp = 844)
@Composable
private fun SplashMidPreview() = SplashFrame(timeMs = { 700f })

@Preview(name = "Splash, end state", widthDp = 390, heightDp = 844)
@Composable
private fun SplashEndPreview() = SplashFrame(timeMs = { SplashTiming.FADE_START })
