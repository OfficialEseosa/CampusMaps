package com.campusmaps.ui.screens

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.data.campus.Campus
import com.campusmaps.data.campus.CampusBuilding
import com.campusmaps.data.campus.CampusId
import com.campusmaps.data.campus.Campuses
import com.campusmaps.data.model.Building
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.CampusPalette
import com.campusmaps.ui.theme.LocalCampusPalette
import com.campusmaps.ui.theme.Sora

// S0b, the buildings of one campus (redesign screen "02 Buildings"). Accent header with rings and the ghost campus
// code, then one row per building. Mapped rows read the real building file; the rest are "Soon" and not tappable.
@Composable
fun BuildingsScreen(
    campus: Campus,
    buildings: List<Building>,
    onBack: () -> Unit,
    onSwap: () -> Unit,
    onBuilding: (String) -> Unit,
) {
    val p = LocalCampusPalette.current
    val other = Campuses.get(if (campus.id == CampusId.GT) CampusId.GSU else CampusId.GT)
    // A listed building is live only when its file loaded; a missing file shows like a "Soon" row.
    val rows = campus.buildings.map { cb -> CampusBuildingEntry(cb, if (cb.soon) null else buildings.firstOrNull { it.id == cb.code }) }
    val mapped = rows.count { it.building != null }
    val soon = rows.size - mapped

    Column(Modifier.fillMaxSize().background(p.surface)) {
        BuildingsHeader(campus, other.name, p, onBack, onSwap)

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 22.dp,
                bottom = 30.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "$mapped building${if (mapped == 1) "" else "s"} mapped · $soon coming soon",
                    style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.3.sp),
                    color = p.muted,
                )
            }
            items(rows, key = { it.info.code }) { row -> CampusBuildingCard(row, p, onBuilding) }
        }
    }
}

// One list entry: the campus listing plus the loaded building, if there is one.
private data class CampusBuildingEntry(val info: CampusBuilding, val building: Building?)

// Accent header: back, swap to the other campus, campus name, "Pick a building".
@Composable
private fun BuildingsHeader(campus: Campus, otherName: String, p: CampusPalette, onBack: () -> Unit, onSwap: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(p.accent)
            .drawBehind { drawCampusRings(Offset(size.width, 0f), 38.dp, 39.5.dp, p.ring) },
    ) {
        CampusGhostCode(campus.code, fontSize = 130.sp, letterSpacing = (-6).sp, color = p.ghost, x = 6.dp, y = 30.dp)

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 26.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(p.chip)
                        .clickable(role = Role.Button, onClickLabel = "Back to campuses", onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.arrowBack, contentDescription = "Back", tint = p.headerText, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.weight(1f))
                // The pill is 36 dp tall; the tappable area is the full 44 dp row height.
                Box(
                    Modifier.height(44.dp).clickable(role = Role.Button, onClickLabel = "Switch campus", onClick = onSwap),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.height(36.dp).clip(CircleShape).background(p.chip).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = p.headerText, modifier = Modifier.size(18.dp))
                        Text(otherName, style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 12.sp), color = p.headerText)
                    }
                }
            }
            Text(
                campus.name,
                modifier = Modifier.padding(top = 18.dp),
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = p.headerText.copy(alpha = 0.85f),
            )
            Text(
                "Pick a building",
                modifier = Modifier.padding(top = 2.dp),
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-1).sp),
                color = p.headerText,
            )
        }
    }
}

// A building row: floor tile, code tag, name, "X floors · Y rooms", then a chevron or a "Soon" pill.
@Composable
private fun CampusBuildingCard(row: CampusBuildingEntry, p: CampusPalette, onBuilding: (String) -> Unit) {
    val b = row.building
    val live = b != null
    val floors = b?.floors?.let { it.last - it.first + 1 } ?: row.info.floors
    val name = b?.name ?: row.info.name
    val sub = if (b != null) "$floors floors · ${b.rooms.size} ${if (b.rooms.size == 1) "room" else "rooms"}" else "$floors floors · mapping soon"

    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (live) 1f else 0.55f)
            .clip(RoundedCornerShape(20.dp))
            .background(p.card)
            .border(BorderStroke(1.dp, p.cardBorder), RoundedCornerShape(20.dp))
            .then(if (live) Modifier.clickable(role = Role.Button) { onBuilding(row.info.code) } else Modifier)
            .testTag("building_${row.info.code}")
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FloorTile(floors, live, p)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                row.info.code,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(p.accent).padding(horizontal = 7.dp, vertical = 2.dp),
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
                color = p.onAccent,
            )
            Text(name, style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 16.sp), color = p.ink)
            Text(sub, style = TextStyle(fontFamily = Sora, fontSize = 12.sp), color = p.muted)
        }
        if (live) {
            Icon(AppIcons.chevronRight, contentDescription = null, tint = p.ink, modifier = Modifier.size(24.dp))
        } else {
            Text(
                "Soon",
                modifier = Modifier.border(1.dp, p.cardBorder, CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = p.muted,
            )
        }
    }
}

// The 64 dp building tile: one slanted bar per floor stacked bottom-up, the top floor white.
@Composable
private fun FloorTile(floors: Int, live: Boolean, p: CampusPalette) {
    // More than 8 bars would not fit the tile; the look matters more than the exact count.
    val n = floors.coerceIn(1, 8)
    Canvas(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(p.deep)) {
        val w = 30.dp.toPx()
        val h = 4.dp.toPx()
        val gap = 3.dp.toPx()
        val total = n * h + (n - 1) * gap
        val bottom = (size.height + total) / 2
        val cx = size.width / 2
        for (i in 0 until n) {
            val color = when {
                !live -> Color.White.copy(alpha = 0.25f)
                i == n - 1 -> Color.White
                else -> p.bar
            }
            val cy = bottom - i * (h + gap) - h / 2
            // skewX(-30deg) around the bar's centre, as in the mock.
            drawContext.canvas.save()
            drawContext.canvas.translate(cx, cy)
            drawContext.canvas.skew(-30f, 0f)
            drawRoundRect(color, topLeft = Offset(-w / 2, -h / 2), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
            drawContext.canvas.restore()
        }
    }
}
