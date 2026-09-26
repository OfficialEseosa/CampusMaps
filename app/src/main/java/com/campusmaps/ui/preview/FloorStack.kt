package com.campusmaps.ui.preview

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.route.Route
import com.campusmaps.ui.theme.ArOverlayColors
import com.campusmaps.ui.theme.Sora
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Pseudo-3D stack of floor slabs (redesign "05 3D preview", CampusMaps.dc.html, "3D floors").
// Same transform as the mock's CSS: each slab is translated up by floor index * gap, then rotateZ(rotation),
// then rotateX(tilt), then a 1100 dp perspective. Floors are drawn bottom to top so upper ones overlap.
// With a route, its indoor points are drawn on their slabs (route bbox fitted into the slab); without one,
// the mock's stylised stubs (entry stub from the left edge, L to the destination ring).
@Composable
fun FloorStack(
    floors: IntRange,
    entryFloor: Int,          // floor where the route enters the building (blue entrance dot + route stub)
    destFloor: Int,           // highlighted floor (glow border/fill + destination ring)
    glow: Color,
    glowFill: Color,
    modifier: Modifier = Modifier,
    rotationDeg: Float = -38f,  // rotateZ
    tiltDeg: Float = 58f,       // rotateX
    compact: Boolean = false,   // small card version: thinner strokes, no labels
    focusFloor: Int? = null,    // when set, other floors fade to 10% alpha
    route: Route? = null,       // optional: draw the real route polyline per floor
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        drawFloorStack(
            floors, entryFloor, destFloor, glow, glowFill, rotationDeg, tiltDeg, compact, focusFloor, route,
            if (compact) null else measurer,
        )
    }
}

private val SlabFill = Color.White.copy(alpha = 0.05f)
private val SlabStroke = Color.White.copy(alpha = 0.22f)
private val CorridorFill = Color.White.copy(alpha = 0.07f)
private val CoreIdle = Color.White.copy(alpha = 0.14f)
private val LabelIdle = Color.White.copy(alpha = 0.55f)

// Slab-local coordinates are y-down in the slab plane (like the mock's divs and CoreBridge's positions), z is up.
private class Projector(
    rotationDeg: Float,
    tiltDeg: Float,
    private val centre: Offset,
    private val perspective: Float,
) {
    private val cr = cos(rotationDeg * PI / 180).toFloat()
    private val sr = sin(rotationDeg * PI / 180).toFloat()
    private val ct = cos(tiltDeg * PI / 180).toFloat()
    private val st = sin(tiltDeg * PI / 180).toFloat()

    // Returns the screen point and the perspective scale at that point.
    fun project(x: Float, y: Float, z: Float): Pair<Offset, Float> {
        // rotateZ
        val xr = x * cr - y * sr
        val yr = x * sr + y * cr
        // rotateX (CSS: positive tilts the top edge away from the viewer)
        val y2 = yr * ct - z * st
        val z2 = yr * st + z * ct
        val p = perspective / max(1f, perspective - z2)
        return Offset(centre.x + xr * p, centre.y + y2 * p) to p
    }

    fun point(x: Float, y: Float, z: Float): Offset = project(x, y, z).first
}

private fun DrawScope.drawFloorStack(
    floors: IntRange,
    entryFloor: Int,
    destFloor: Int,
    glow: Color,
    glowFill: Color,
    rotationDeg: Float,
    tiltDeg: Float,
    compact: Boolean,
    focusFloor: Int?,
    route: Route?,
    measurer: TextMeasurer?,
) {
    val list = floors.toList()
    if (list.isEmpty()) return
    val n = list.size

    // Slab size: 220 x 150 in the mock on a ~390 wide phone; keep that ratio and fit the canvas.
    val slabW = min(size.width * if (compact) 0.60f else 0.58f, size.height * if (compact) 0.95f else 0.80f)
    val slabH = slabW * 150f / 220f
    val gap = when {
        compact -> if (n > 4) 13.dp else 22.dp
        else -> if (n > 4) 34.dp else 52.dp
    }.toPx()
    val zMid = (n - 1) * gap / 2f
    val pj = Projector(rotationDeg, tiltDeg, Offset(size.width / 2f, size.height / 2f), 1100.dp.toPx())

    val strokeW = (if (compact) 1.dp else 1.5.dp).toPx()
    val routeW = (if (compact) 2.5.dp else 5.dp).toPx()
    val glowW = routeW * 3f
    val corner = (if (compact) 7.dp else 14.dp).toPx()
    val halfW = slabW / 2f
    val halfH = slabH / 2f

    val lowFloor = min(entryFloor, destFloor)
    val highFloor = max(entryFloor, destFloor)
    fun zOf(floor: Int) = list.indexOf(floor).coerceAtLeast(0) * gap - zMid
    fun alphaOf(floor: Int) = if (focusFloor != null && focusFloor != floor) 0.1f else 1f

    // Route points (indoor only) mapped into slab-local coordinates, keeping the aspect ratio.
    val indoor = route?.points?.filter { !it.node.isOutdoor }.orEmpty()
    val local: List<Triple<Float, Float, Int>> = if (indoor.isEmpty()) emptyList() else {
        var minX = indoor.minOf { it.position.x }
        var maxX = indoor.maxOf { it.position.x }
        var minY = indoor.minOf { it.position.y }
        var maxY = indoor.maxOf { it.position.y }
        if (maxX - minX < 4) { minX -= 2; maxX += 2 }
        if (maxY - minY < 4) { minY -= 2; maxY += 2 }
        val innerW = slabW * 0.76f
        val innerH = slabH * 0.70f
        val s = min(innerW / (maxX - minX), innerH / (maxY - minY)).toFloat()
        val cx = ((minX + maxX) / 2).toFloat()
        val cy = ((minY + maxY) / 2).toFloat()
        indoor.map { Triple((it.position.x.toFloat() - cx) * s, (it.position.y.toFloat() - cy) * s, it.floor) }
    }

    fun polygon(pts: List<Offset>): Path = Path().apply {
        pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }

    // A slab-plane rounded rectangle, sampled and projected.
    fun roundedRect(l: Float, t: Float, r: Float, b: Float, rad: Float, z: Float): Path {
        val rr = min(rad, min((r - l) / 2f, (b - t) / 2f))
        val pts = ArrayList<Offset>()
        val corners = listOf(
            Triple(r - rr, t + rr, -90.0), Triple(r - rr, b - rr, 0.0),
            Triple(l + rr, b - rr, 90.0), Triple(l + rr, t + rr, 180.0),
        )
        for ((cx, cy, start) in corners) {
            for (k in 0..4) {
                val a = (start + k * 22.5) * PI / 180
                pts += pj.point(cx + rr * cos(a).toFloat(), cy + rr * sin(a).toFloat(), z)
            }
        }
        return polygon(pts)
    }

    fun ring(x: Float, y: Float, z: Float, radius: Float): Path {
        val pts = (0 until 24).map { k ->
            val a = k * 2 * PI / 24
            pj.point(x + radius * cos(a).toFloat(), y + radius * sin(a).toFloat(), z)
        }
        return polygon(pts)
    }

    fun glowLine(pts: List<Offset>, alpha: Float) {
        if (pts.size < 2) return
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
        drawPath(path, ArOverlayColors.arrowEdge.copy(alpha = 0.35f * alpha), style = Stroke(glowW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, ArOverlayColors.arrowCore.copy(alpha = alpha), style = Stroke(routeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun destRing(x: Float, y: Float, z: Float, alpha: Float) {
        val r = (if (compact) 6.dp else 11.dp).toPx()
        drawPath(ring(x, y, z, r * 1.5f), glow.copy(alpha = 0.25f * alpha))
        val path = ring(x, y, z, r - (if (compact) 1.dp else 2.dp).toPx())
        drawPath(path, Color.Black.copy(alpha = 0.4f * alpha))
        drawPath(path, glow.copy(alpha = alpha), style = Stroke((if (compact) 2.5.dp else 4.dp).toPx()))
    }

    fun entranceDot(x: Float, y: Float, z: Float, alpha: Float) {
        val (c, p) = pj.project(x, y, z)
        val r = (if (compact) 5.dp else 8.dp).toPx() * p
        drawCircle(Color.White.copy(alpha = alpha), r, c)
        drawCircle(ArOverlayColors.user.copy(alpha = alpha), r - (if (compact) 1.5.dp else 2.dp).toPx(), c)
    }

    for (floor in list) {
        val z = zOf(floor)
        val a = alphaOf(floor)
        val isDest = floor == destFloor
        val isEntry = floor == entryFloor

        // Slab
        val slab = roundedRect(-halfW, -halfH, halfW, halfH, corner, z)
        drawPath(slab, (if (isDest) glowFill else SlabFill).let { it.copy(alpha = it.alpha * a) })
        drawPath(slab, (if (isDest) glow else SlabStroke).let { it.copy(alpha = it.alpha * a) }, style = Stroke(strokeW))

        // Corridor cross (the mock's two faint bars)
        if (!compact) {
            val bar = 5.dp.toPx()
            drawPath(roundedRect(-halfW * 0.84f, -bar, halfW * 0.84f, bar, bar, z), CorridorFill.copy(alpha = CorridorFill.alpha * a))
            drawPath(roundedRect(-bar, -halfH * 0.8f, bar, halfH * 0.8f, bar, z), CorridorFill.copy(alpha = CorridorFill.alpha * a))
        }

        // Core (lift / stair shaft): lit on every floor the route rides through
        val lit = destFloor != entryFloor && floor in lowFloor..highFloor
        val coreHalf = (if (compact) 5.dp else 10.dp).toPx()
        val core = roundedRect(-coreHalf, -coreHalf, coreHalf, coreHalf, coreHalf / 2f, z)
        if (lit) drawPath(roundedRect(-coreHalf * 1.8f, -coreHalf * 1.8f, coreHalf * 1.8f, coreHalf * 1.8f, coreHalf, z), ArOverlayColors.arrowEdge.copy(alpha = 0.35f * a))
        drawPath(core, if (lit) ArOverlayColors.arrowCore.copy(alpha = a) else CoreIdle.copy(alpha = CoreIdle.alpha * a))

        // Label near the bottom-left corner
        if (measurer != null) {
            val at = pj.point(-halfW + 10.dp.toPx(), halfH - 6.dp.toPx(), z)
            val style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = (if (isDest) glow else LabelIdle).let { it.copy(alpha = it.alpha * a) })
            val layout = measurer.measure("F$floor", style)
            drawText(layout, topLeft = Offset(at.x, at.y - layout.size.height))
        }

        if (local.isNotEmpty()) {
            // Real route: the stretch on this floor, plus the connector down to the previous floor where it changed floor.
            var run = ArrayList<Offset>()
            for (i in local.indices) {
                val (x, y, f) = local[i]
                if (f == floor) {
                    run += pj.point(x, y, z)
                } else {
                    glowLine(run, a); run = ArrayList()
                }
                if (i > 0 && f == floor && local[i - 1].third != floor) {
                    val prev = local[i - 1]
                    val lower = min(prev.third, f)
                    // Draw the riser with the upper of the two floors so it overlaps the lower slab.
                    if (floor != lower) glowLine(listOf(pj.point(prev.first, prev.second, zOf(prev.third)), pj.point(x, y, z)), a)
                } else if (i > 0 && f != floor && local[i - 1].third == floor && f < floor) {
                    // Going down from this floor: this is the upper end.
                    val prev = local[i - 1]
                    glowLine(listOf(pj.point(prev.first, prev.second, z), pj.point(x, y, zOf(f))), a)
                }
            }
            glowLine(run, a)
            val first = local.first()
            if (first.third == floor) entranceDot(first.first, first.second, z, a)
            val last = local.last()
            if (last.third == floor) destRing(last.first, last.second, z, a)
        } else {
            // Stylised stubs from the mock.
            if (isEntry) glowLine(listOf(pj.point(-halfW, 0f, z), pj.point(0f, 0f, z)), a)
            if (isDest) {
                val top = -halfH + slabH * 0.22f
                val right = slabW * 0.32f
                glowLine(listOf(pj.point(0f, top + slabH * 0.28f, z), pj.point(0f, top, z), pj.point(right, top, z)), a)
                destRing(right, top, z, a)
            }
            if (isEntry) entranceDot(-halfW, 0f, z, a)
        }
    }
}
