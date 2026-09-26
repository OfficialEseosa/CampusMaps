package com.campusmaps.ui.transition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.geo.HandoffController
import com.campusmaps.geo.HandoffPhase
import com.campusmaps.geo.HandoffUi
import com.campusmaps.ui.theme.Sora
import kotlinx.coroutines.delay

/** Length of the map-to-AR hand-off. */
const val HANDOFF_MS = 900

/** How long the "Almost there" card stays before the hand-off plays by itself (a tap starts it at once). */
const val CARD_MS = 1600L

private val ClayDeep = Color(0xFFA85F33)

/**
 * The map-to-AR hand-off, no jump cut (LEG 2). At [progress] 0 only the map shows; at 1 only AR. In between, the AR layer
 * is composed underneath and fades in while the map tilts back (up to 55 degrees), zooms into the user's dot at [focus]
 * (a fraction of the map's size, default centre) and fades out over the last 60 %.
 *
 * The orchestrator wires ExploreScreen as [mapContent] and GuidanceScreen as [arContent]; [progress] comes from
 * [rememberHandoffProgress].
 */
@Composable
fun MapToArTransition(
    mapContent: @Composable () -> Unit,
    arContent: @Composable () -> Unit,
    progress: Float,
    modifier: Modifier = Modifier,
    focus: TransformOrigin = TransformOrigin.Center,
) {
    val p = progress.coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().testTag("mapToAr")) {
        if (p > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = (p / 0.7f).coerceAtMost(1f) }) { arContent() }
        }
        if (p < 1f) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = focus
                    rotationX = 55f * p
                    cameraDistance = 12f * density
                    val s = 1f + 1.6f * p
                    scaleX = s; scaleY = s
                    alpha = if (p < 0.4f) 1f else (1f - (p - 0.4f) / 0.6f)
                },
            ) { mapContent() }
        }
    }
}

/**
 * Drives [MapToArTransition]'s progress from the hand-off phase: 0 on MAP and CARD, animates to 1 over [HANDOFF_MS]
 * on ANIMATING, then calls [onDone] (wire it to [HandoffController.animationDone]); 1 on AR.
 */
@Composable
fun rememberHandoffProgress(phase: HandoffPhase, onDone: () -> Unit): Float {
    val anim = remember { Animatable(if (phase == HandoffPhase.AR) 1f else 0f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(phase) {
        when (phase) {
            HandoffPhase.MAP, HandoffPhase.CARD -> anim.snapTo(0f)
            HandoffPhase.ANIMATING -> { anim.animateTo(1f, tween(HANDOFF_MS, easing = FastOutSlowInEasing)); done() }
            HandoffPhase.AR -> anim.snapTo(1f)
        }
    }
    return anim.value
}

/** "Almost there. Point your camera ahead": Clay Deep card, white Sora text. Tap to go now; advances by itself after [CARD_MS]. */
@Composable
fun HandoffCard(onGo: () -> Unit, modifier: Modifier = Modifier, entranceName: String? = null) {
    val go by rememberUpdatedState(onGo)
    LaunchedEffect(Unit) { delay(CARD_MS); go() }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ClayDeep)
            .clickable { go() }
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag("handoffCard"),
    ) {
        Text("Almost there. Point your camera ahead", color = Color.White, fontFamily = Sora, fontSize = 20.sp,
            lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold)
        if (entranceName != null) {
            Text(entranceName, color = Color.White.copy(alpha = 0.8f), fontFamily = Sora, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Everything in one place for the orchestrator: the transition plus the card over the map.
 * `ui` is `controller.ui.collectAsState().value`.
 */
@Composable
fun MapToArHost(
    ui: HandoffUi,
    controller: HandoffController,
    mapContent: @Composable () -> Unit,
    arContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    focus: TransformOrigin = TransformOrigin.Center,
) {
    val progress = rememberHandoffProgress(ui.phase) { controller.animationDone() }
    Box(modifier.fillMaxSize()) {
        MapToArTransition(mapContent, arContent, progress, focus = focus)
        AnimatedVisibility(
            visible = ui.phase == HandoffPhase.CARD,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            HandoffCard(onGo = { controller.startHandoff() }, entranceName = ui.entrance?.name)
        }
    }
}

/** Stand-in for the map agent's ExploreScreen: street grid, a building block and the user's dot at the centre. */
@Composable
fun FakeMapPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().background(Color(0xFFF3ECE6))) {
        val street = Color(0xFFFFFFFF)
        val step = size.minDimension / 5
        var x = 0f
        while (x < size.width) { drawRect(street, Offset(x, 0f), androidx.compose.ui.geometry.Size(18f, size.height)); x += step }
        var y = 0f
        while (y < size.height) { drawRect(street, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 18f)); y += step }
        drawRect(Color(0xFFE2CFC2), Offset(size.width * 0.55f, size.height * 0.2f), androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height * 0.22f))
        drawCircle(Color(0x332F6FDE), radius = 48f, center = center)
        drawCircle(Color.White, radius = 18f, center = center)
        drawCircle(Color(0xFF2F6FDE), radius = 13f, center = center)
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun MapToArTransitionPreview() {
    MapToArTransition(
        mapContent = { FakeMapPlaceholder() },
        arContent = { Box(Modifier.fillMaxSize().background(Color(0xFF141110))) },
        progress = 0.45f,
    )
}

@Preview(widthDp = 360)
@Composable
private fun HandoffCardPreview() {
    HandoffCard(onGo = {}, entranceName = "Walters main entrance")
}
