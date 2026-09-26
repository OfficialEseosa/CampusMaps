package com.campusmaps.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.guidance.GlassesPhase
import com.campusmaps.guidance.GuidanceState
import com.campusmaps.route.Formats
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusmaps.ui.MainViewModel
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.AppTextStyles
import com.campusmaps.ui.theme.ArOverlayColors
import com.campusmaps.ui.theme.Sora

// Everything S3 shows that comes from the glasses (not from the route).
data class GlassesUi(
    val connected: Boolean,
    val phase: GlassesPhase,
    val seen: String?,
    val lastStill: ImageBitmap?,
)

// S3 Glasses mode (section 8). Pure black, very high contrast (it is filmed).
// No text darker than 0xFFB0BEC5.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassesScreen(
    state: GuidanceState,
    glasses: GlassesUi,
    showFakeStep: Boolean,
    onRepeat: () -> Unit,
    onStop: () -> Unit,
    onFakeStep: () -> Unit,
    // Arrival buttons (WHATS-LEFT 4). Null = use the activity's MainViewModel (done / endGuidance),
    // so the call site in CampusMapsApp works unchanged; pass them explicitly when wiring is next touched.
    onDone: (() -> Unit)? = null,
    onBackToRoutes: (() -> Unit)? = null,
) {
    var thumbnailHidden by rememberSaveable { mutableStateOf(false) }

    // On arrival the instruction says so, and the "Next" line becomes the door side.
    val instruction = if (state.arrived) "You have arrived" else state.bannerText
    val nextLine = when {
        state.arrived -> state.step.text
        state.nextStep != null -> "Next: ${state.nextStep.text}"
        else -> null
    }
    val icon = if (state.arrived) AppIcons.check else AppIcons.forStep(state.step.kind)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp)
            .testTag("glassesScreen"),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. Header row
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "GLASSES MODE",
                    color = Color.White,
                    fontFamily = Sora,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                ConnectionChip(glasses.connected)
            }

            // 2. Seen line
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.visibility, contentDescription = null, tint = ArOverlayColors.glassesMuted, modifier = Modifier.size(20.dp))
                Text(
                    buildAnnotatedString {
                        append("Seen: ")
                        if (glasses.seen != null) {
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(glasses.seen) }
                        } else {
                            append("nothing yet")
                        }
                    },
                    color = ArOverlayColors.glassesMuted,
                    fontFamily = Sora,
                    fontSize = 16.sp,
                )
            }

            // 3. Instruction
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (state.arrived) ArOverlayColors.arrived else ArOverlayColors.arrowCore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(40.dp))
                }
                // No Crossfade: on arrival it left a faint ghost of the previous instruction behind
                // "You have arrived" (filmed). One text at a time, swapped at once.
                Text(instruction, style = AppTextStyles.glassesInstruction, color = Color.White, modifier = Modifier.testTag("glassesInstruction"))
            }

            // 4. Next line (door side on arrival)
            nextLine?.let { Text(it, style = AppTextStyles.glassesNext, color = ArOverlayColors.glassesMuted) }

            // 5. Live thumbnail from the glasses
            AnimatedVisibility(visible = !thumbnailHidden, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
                Thumbnail(glasses.lastStill, onHide = { thumbnailHidden = true })
            }
            if (thumbnailHidden) {
                OutlinedButton(
                    onClick = { thumbnailHidden = false },
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, ArOverlayColors.glassesOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Show glasses view", fontFamily = Sora, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 6. Floor and the Looking / Recognising / Speaking cycle
            // On the S25 (384 dp wide) the pills beside the floor wrapped "Speaking" onto a second line (docs/22 #7):
            // the floor goes above, the three pills get the full width.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Formats.floorLong(state.floor), color = Color.White, fontFamily = Sora, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassesPhase.entries.forEach { phase -> PhasePill(phase, active = phase == glasses.phase && glasses.connected) }
                }
            }

            // 8. Debug only (hidden in demo mode, and gone after arrival)
            if (showFakeStep && !state.arrived) {
                OutlinedButton(
                    onClick = onFakeStep,
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, ArOverlayColors.glassesOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ArOverlayColors.glassesMuted),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Fake step", fontFamily = Sora, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 7a. Arrived: Done (to S1) and Back to routes (to S1b), like S2; Repeat stays for the door side.
        if (state.arrived) {
            val vm = if (onDone == null || onBackToRoutes == null) viewModel<MainViewModel>() else null
            GlassesArrivalButtons(
                onRepeat = onRepeat,
                onDone = onDone ?: vm!!::done,
                onBackToRoutes = onBackToRoutes ?: vm!!::endGuidance,
            )
            return@Column
        }

        // 7. Repeat and Stop, half width each, 80 dp tall.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRepeat,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(2.dp, Color.White),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .testTag("repeatButton"),
            ) {
                Icon(AppIcons.replay, contentDescription = null, modifier = Modifier.size(26.dp))
                Text("Repeat", fontFamily = Sora, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp))
            }
            Button(
                onClick = onStop,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ArOverlayColors.stop, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .testTag("stopButton"),
            ) {
                Icon(AppIcons.stop, contentDescription = null, modifier = Modifier.size(26.dp))
                Text("Stop", fontFamily = Sora, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun GlassesArrivalButtons(onRepeat: () -> Unit, onDone: () -> Unit, onBackToRoutes: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ArOverlayColors.arrived, contentColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .testTag("doneButton"),
        ) {
            Icon(AppIcons.check, contentDescription = null, modifier = Modifier.size(26.dp))
            Text("Done", fontFamily = Sora, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onRepeat,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(2.dp, Color.White),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .testTag("repeatButton"),
            ) {
                Icon(AppIcons.replay, contentDescription = null, modifier = Modifier.size(22.dp))
                Text("Repeat", fontFamily = Sora, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = onBackToRoutes,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(2.dp, ArOverlayColors.glassesOutline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .testTag("backToRoutesButton"),
            ) {
                Text("Back to routes", fontFamily = Sora, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// Green dot + "Glasses connected", or a grey outline "Not connected".
@Composable
private fun ConnectionChip(connected: Boolean) {
    if (connected) {
        Row(
            Modifier
                .height(34.dp)
                .clip(CircleShape)
                .background(ArOverlayColors.onArrived)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ArOverlayColors.arrived),
            )
            Text("Glasses connected", color = ArOverlayColors.arrived, fontFamily = Sora, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Row(
            Modifier
                .height(34.dp)
                .border(1.5.dp, ArOverlayColors.glassesOutline, CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Not connected", color = ArOverlayColors.glassesMuted, fontFamily = Sora, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// 190 dp tall, 18 dp corners, dashed outline when empty, "Hide" pill top right.
@Composable
private fun Thumbnail(still: ImageBitmap?, onHide: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(18.dp)),
    ) {
        if (still != null) {
            Image(still, contentDescription = "Last still from the glasses", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = ArOverlayColors.glassesOutline,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                )
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.photoCamera, contentDescription = null, tint = ArOverlayColors.glassesOutline, modifier = Modifier.size(28.dp))
                Text("Waiting for the first still", color = ArOverlayColors.glassesOutline, fontFamily = Sora, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        OutlinedButton(
            onClick = onHide,
            shape = CircleShape,
            border = BorderStroke(1.5.dp, ArOverlayColors.glassesOutline),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black, contentColor = Color.White),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .height(40.dp),
        ) {
            Text("Hide", fontFamily = Sora, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Active pill: white fill, black text, the phase's icon (eye / search / speaker, docs/22 #14). Inactive: outlined.
@Composable
private fun PhasePill(phase: GlassesPhase, active: Boolean) {
    val shape = CircleShape
    Row(
        Modifier
            .height(34.dp)
            .clip(shape)
            .then(if (active) Modifier.background(Color.White) else Modifier.border(1.5.dp, ArOverlayColors.glassesOutline, shape))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val icon = when (phase) {
            GlassesPhase.LOOKING -> AppIcons.visibility
            GlassesPhase.RECOGNISING -> AppIcons.search
            GlassesPhase.SPEAKING -> AppIcons.volumeUp
        }
        if (active) Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
        Text(
            phase.label,
            color = if (active) Color.Black else ArOverlayColors.glassesMuted,
            fontFamily = Sora,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
    }
}
