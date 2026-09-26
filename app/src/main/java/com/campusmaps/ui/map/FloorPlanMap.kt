package com.campusmaps.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.Point
import com.campusmaps.routing.Formats
import com.campusmaps.ui.theme.ArOverlayColors
import com.campusmaps.ui.theme.Sora
import kotlin.math.min

// Offline minimap drawn from the building file (section 6, bottom row).
// Corridors mapCorridor 10 dp, route arrowCore 4 dp, user dot with white ring and soft halo,
// destination ring. North up. Always uses ArOverlayColors (it sits on the camera).
@Composable
fun FloorPlanMap(state: MapViewState, modifier: Modifier = Modifier, compact: Boolean = true) {
    val floorLabel = Formats.floorShort(state.floor)
    Box(modifier.semantics { contentDescription = "Floor plan minimap, ${Formats.floorLong(state.floor)}" }) {
        Canvas(Modifier.matchParentSize()) {
            drawFloorPlan(state, compact)
        }
        Text(
            floorLabel,
            color = ArOverlayColors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Sora,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 8.dp),
        )
    }
}

private fun DrawScope.drawFloorPlan(state: MapViewState, compact: Boolean) {
    val building = state.building
    val floor = state.floor

    // Which nodes are on this floor? Outdoor nodes count as part of the ground floor while outside.
    val onFloor = building.nodes.values.filter { it.floor == floor && (!it.isOutdoor || state.outdoors) }
    val routePoints = state.route?.points.orEmpty()
    val routeOnFloor = routePoints.filter { it.floor == floor && (!it.node.isOutdoor || state.outdoors) }
    val boundsPoints = onFloor.map { it.position } + routeOnFloor.map { it.position } + listOfNotNull(state.user)
    if (boundsPoints.isEmpty()) return

    // Fit the floor into the box with padding, keeping the aspect ratio.
    val pad = (if (compact) 18.dp else 28.dp).toPx()
    var minX = boundsPoints.minOf { it.x }
    var maxX = boundsPoints.maxOf { it.x }
    var minY = boundsPoints.minOf { it.y }
    var maxY = boundsPoints.maxOf { it.y }
    // Avoid dividing by zero on a single point floor.
    if (maxX - minX < 10) { minX -= 5; maxX += 5 }
    if (maxY - minY < 10) { minY -= 5; maxY += 5 }
    val scale = min((size.width - 2 * pad) / (maxX - minX), (size.height - 2 * pad) / (maxY - minY)).toFloat()
    val offsetX = (size.width - (maxX - minX).toFloat() * scale) / 2f
    val offsetY = (size.height - (maxY - minY).toFloat() * scale) / 2f
    fun map(p: Point) = Offset(offsetX + ((p.x - minX).toFloat() * scale), offsetY + ((p.y - minY).toFloat() * scale))

    // Corridors (walk edges on this floor).
    val corridorWidth = (if (compact) 10.dp else 14.dp).toPx()
    for (edge in building.edges) {
        if (edge.kind != EdgeKind.WALK) continue
        val a = building.nodes[edge.from] ?: continue
        val b = building.nodes[edge.to] ?: continue
        if (a.floor != floor || b.floor != floor) continue
        val outdoor = a.isOutdoor || b.isOutdoor
        if (outdoor && !state.outdoors) continue
        drawLine(
            color = if (outdoor) ArOverlayColors.mapCorridor.copy(alpha = 0.55f) else ArOverlayColors.mapCorridor,
            start = map(a.position),
            end = map(b.position),
            strokeWidth = if (outdoor) corridorWidth * 0.5f else corridorWidth,
            cap = StrokeCap.Round,
        )
    }

    // Route on this floor.
    if (routeOnFloor.size >= 2) {
        val path = Path()
        var started = false
        for (i in routePoints.indices) {
            val p = routePoints[i]
            val visible = p.floor == floor && (!p.node.isOutdoor || state.outdoors)
            if (!visible) { started = false; continue }
            val o = map(p.position)
            if (!started) { path.moveTo(o.x, o.y); started = true } else path.lineTo(o.x, o.y)
        }
        drawPath(
            path,
            color = ArOverlayColors.arrowCore,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // Destination ring (green when arrived).
    val destination = state.route?.destination
    if (destination != null && destination.floor == floor) {
        val c = map(destination.position)
        drawCircle(
            color = if (state.arrived) ArOverlayColors.arrived else ArOverlayColors.destinationRing,
            radius = (if (state.arrived) 10.dp else 8.dp).toPx(),
            center = c,
            style = Stroke(width = 3.dp.toPx()),
        )
    }

    // User dot: soft halo, blue dot, white 2 dp ring.
    state.user?.let { u ->
        val c = map(u)
        drawCircle(ArOverlayColors.user.copy(alpha = 0.3f), radius = 11.dp.toPx(), center = c)
        drawCircle(ArOverlayColors.user, radius = 6.dp.toPx(), center = c)
        drawCircle(ArOverlayColors.text, radius = 6.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
    }
}
