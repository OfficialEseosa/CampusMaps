package com.campusmaps.ui.screens

import androidx.compose.ui.platform.testTag
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.data.campus.Campus
import com.campusmaps.data.campus.CampusId
import com.campusmaps.data.campus.Campuses
import com.campusmaps.data.model.Building
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.CampusPalettes
import com.campusmaps.ui.theme.Sora
import kotlin.math.hypot
import kotlin.math.max

// S0, the campus picker (redesign screen "01 Campus"). Neutral cream background, one big card per campus in its
// own colours. Shown before a campus is chosen, so it does not read LocalCampusPalette.
@Composable
fun CampusScreen(
    buildings: List<Building>,
    onCampus: (CampusId) -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CampusPalettes.pickerSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Logo tile, app name, settings.
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(CampusPalettes.pickerInk),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = CampusPalettes.pickerSurface, modifier = Modifier.size(20.dp))
            }
            Text(
                "CampusMaps",
                modifier = Modifier.padding(start = 10.dp).weight(1f),
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.2).sp),
                color = CampusPalettes.pickerInk,
            )
            IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)) {
                Icon(AppIcons.settings, contentDescription = "Settings", tint = CampusPalettes.pickerMuted, modifier = Modifier.size(24.dp))
            }
        }

        // Headline and one line on what the app does.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Choose your campus",
                style = TextStyle(
                    fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 31.sp,
                    lineHeight = 34.sp, letterSpacing = (-1).sp,
                ),
                color = CampusPalettes.pickerInk,
            )
            Text(
                "Indoor directions that float in front of you — on your phone, glasses or watch.",
                style = TextStyle(fontFamily = Sora, fontSize = 14.sp, lineHeight = 20.sp),
                color = CampusPalettes.pickerMuted,
            )
        }

        // The two campus cards share the rest of the height.
        Campuses.all.forEach { campus ->
            CampusCard(campus, mappedCount(campus, buildings), onClick = { onCampus(campus.id) })
        }
    }
}

// Mapped buildings of this campus that actually loaded (a file in assets/buildings).
private fun mappedCount(campus: Campus, buildings: List<Building>): Int =
    campus.mappedCodes.count { code -> buildings.any { it.id == code } }

// One campus card: rings, ghost code, name, "AR ready", device chips, arrow.
@Composable
private fun ColumnScope.CampusCard(campus: Campus, mapped: Int, onClick: () -> Unit) {
    val p = CampusPalettes.of(campus.id)
    val ink = p.headerText
    // Card rings are a touch lighter on gold than the header rings (mock: .11 vs .12).
    val ring = if (campus.id == CampusId.GT) Color(0x1C003057) else Color(0x1FFFFFFF)
    val subAlpha = if (campus.id == CampusId.GT) 0.8f else 0.85f

    // Press feedback is a slight shrink, like the mock's :active scale.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "cardPress")

    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(28.dp))
            .background(p.accent)
            .drawBehind { drawCampusRings(Offset(size.width * 1.05f, size.height * -0.05f), 40.dp, 41.5.dp, ring) }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag("campus_${campus.code}"),
    ) {
        // Big faded code in the bottom right corner, cut by the card edge.
        CampusGhostCode(campus.code, fontSize = 170.sp, letterSpacing = (-8).sp, color = p.ghost, x = 14.dp, y = 40.dp)

        Column(
            Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        campus.code,
                        style = TextStyle(
                            fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 58.sp,
                            lineHeight = 58.sp, letterSpacing = (-2.5).sp,
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        ),
                        color = ink,
                    )
                    Text(campus.name, style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 19.sp), color = ink)
                    Text(
                        "${campus.area} · $mapped building${if (mapped == 1) "" else "s"} mapped",
                        style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = 13.sp),
                        color = ink.copy(alpha = subAlpha),
                    )
                }
                // "AR ready" pill with a dot.
                Row(
                    Modifier.clip(CircleShape).background(p.chip).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(ink))
                    Text("AR ready", style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 11.sp), color = ink)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                // Phone, glasses, watch: the three ways to be guided.
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceChip(Icons.Rounded.Smartphone, p.chip, ink)
                    DeviceChip(AppIcons.eyeglasses, p.chip, ink)
                    DeviceChip(Icons.Rounded.Watch, p.chip, ink)
                }
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = p.accent, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceChip(icon: ImageVector, bg: Color, tint: Color) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

// A huge faded code pinned to the bottom right and pushed past the edge by (x, y); the parent clips it.
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.CampusGhostCode(
    text: String,
    fontSize: TextUnit,
    letterSpacing: TextUnit,
    color: Color,
    x: Dp,
    y: Dp,
) {
    Text(
        text,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = x, y = y)
            .wrapContentSize(Alignment.BottomEnd, unbounded = true),
        style = TextStyle(
            fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = fontSize,
            lineHeight = fontSize, letterSpacing = letterSpacing,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

// Concentric rings like the mock's repeating-radial-gradient: a 1.5 dp ring every `step`, the first one `gap` out.
internal fun DrawScope.drawCampusRings(center: Offset, gap: Dp, step: Dp, color: Color) {
    val strokePx = 1.5.dp.toPx()
    val stepPx = step.toPx()
    // Far enough to reach the farthest corner.
    val reach = max(
        max(hypot(center.x, center.y), hypot(size.width - center.x, center.y)),
        max(hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y)),
    )
    var r = gap.toPx() + strokePx / 2
    while (r - strokePx < reach) {
        drawCircle(color, radius = r, center = center, style = Stroke(width = strokePx))
        r += stepPx
    }
}
