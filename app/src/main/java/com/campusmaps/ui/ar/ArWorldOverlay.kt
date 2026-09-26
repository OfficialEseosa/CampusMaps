package com.campusmaps.ui.ar

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
import com.campusmaps.data.model.Point
import com.campusmaps.guidance.GuidanceState
import com.campusmaps.route.Route
import com.campusmaps.route.Side
import com.campusmaps.route.StepKind
import com.campusmaps.ui.theme.ArOverlayColors
import com.campusmaps.ui.theme.Sora
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

// Draws the AR world objects from section 9 on top of the camera image:
//  - chevron chain on the floor every 1.5 m, full opacity to 10 m, fading to 0 at 15 m
//  - a larger next turn arrow at the decision point, bent toward the turn
//  - the destination pin, 1.5 m above the floor, facing the camera
//
// This is a screen space stand-in for the 3D scene: world points are projected with a simple
// pinhole camera (1.45 m high, tilted 15 degrees down, 60 degree field of view). When the ARCore
// scene is ready, the 3D team renders the same objects with the same colors and sizes, and this
// composable goes away.
@Composable
fun ArWorldOverlay(state: GuidanceState, arrowsAlpha: Float, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        val camera = Camera(
            user = state.pose.position,
            heading = state.pose.headingRad,
            width = size.width,
            height = size.height,
        )
        if (!state.arrived && arrowsAlpha > 0.01f) {
            drawChevrons(camera, state, arrowsAlpha)
            drawNextTurnArrow(camera, state, arrowsAlpha)
        }
        drawDestinationPin(camera, state, textMeasurer)
    }
}

// Pinhole camera looking along the student's heading.
private class Camera(val user: Point, val heading: Double, val width: Float, val height: Float) {
    private val heightM = 1.45
    private val pitch = Math.toRadians(15.0)
    val focalPx = (width / 2f) / tan(Math.toRadians(30.0)).toFloat()
    private val cx = width / 2f
    private val cy = height / 2f

    // World point (floor metres + height above floor) -> screen pixels, or null if behind us.
    fun project(p: Point, heightAboveFloor: Double = 0.0): Pair<Offset, Float>? {
        val dx = p.x - user.x
        val dy = p.y - user.y
        val forward = dx * cos(heading) + dy * sin(heading)
        val right = -dx * sin(heading) + dy * cos(heading)
        val down = heightM - heightAboveFloor
        val zc = forward * cos(pitch) + down * sin(pitch)
        val yc = down * cos(pitch) - forward * sin(pitch)
        if (zc < 0.4) return null
        val sx = cx + focalPx * (right / zc).toFloat()
        val sy = cy + focalPx * (yc / zc).toFloat()
        return Offset(sx, sy) to zc.toFloat()
    }

    // Metres at the forward/right offset from the student (for objects placed relative to us).
    fun relative(forward: Double, right: Double): Point = Point(
        user.x + forward * cos(heading) - right * sin(heading),
        user.y + forward * sin(heading) + right * cos(heading),
    )
}

// A point along the walking part of the route, with the walking direction there.
private data class Along(val position: Point, val direction: Point)

// Finds the spot `distance` metres along the route, but only on the given floor and before
// the next elevator / stairs (chevrons never continue past a floor change).
private fun pointAlong(route: Route, distance: Double, floor: Int, fromSegment: Int): Along? {
    val points = route.points
    for (i in fromSegment.coerceAtLeast(0) until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        if (a.floor != b.floor) return null
        if (a.floor != floor) continue
        if (distance < a.cumulativeM - 1e-6) return null
        if (distance <= b.cumulativeM) {
            val length = b.cumulativeM - a.cumulativeM
            if (length < 1e-6) continue
            val t = (distance - a.cumulativeM) / length
            val dir = Point((b.position.x - a.position.x) / length, (b.position.y - a.position.y) / length)
            return Along(a.position + (b.position - a.position) * t, dir)
        }
    }
    return null
}

private fun DrawScope.drawChevrons(camera: Camera, state: GuidanceState, alpha: Float) {
    val spacing = 1.5
    val along = state.progress.alongM
    var s = ceil((along + 0.8) / spacing) * spacing
    val turnNode = state.route.points.getOrNull(state.step.startIndex)?.position
    val drawn = mutableListOf<Triple<Path, Float, Float>>()
    while (s - along <= 15.0) {
        val spot = pointAlong(state.route, s, state.floor, state.progress.segmentIndex) ?: break
        val distance = hypot(spot.position.x - camera.user.x, spot.position.y - camera.user.y)
        // Leave room around the next turn arrow so they do not overlap.
        val nearTurn = turnNode != null && isTurn(state.step.kind) && spot.position.distanceTo(turnNode) < 1.6
        if (!nearTurn) {
            val fade = when {
                distance <= 10.0 -> 1f
                distance >= 15.0 -> 0f
                else -> ((15.0 - distance) / 5.0).toFloat()
            }
            chevronPath(camera, spot)?.let { (path, zc) -> drawn += Triple(path, fade * alpha, zc) }
        }
        s += spacing
    }
    // Far ones first so near ones sit on top.
    for ((path, a, zc) in drawn.sortedByDescending { it.third }) {
        if (a <= 0.01f) continue
        val edge = (24f / 1000f) * camera.focalPx / zc // Design stroke is 24 units at 1:1000 scale
        drawPath(path, ArOverlayColors.arrowEdge.copy(alpha = 0.28f * a), style = Stroke(width = edge * 3.5f, join = StrokeJoin.Round))
        drawPath(path, ArOverlayColors.arrowCore.copy(alpha = a))
        drawPath(path, ArOverlayColors.arrowEdge.copy(alpha = a), style = Stroke(width = edge.coerceAtLeast(1.5f), join = StrokeJoin.Round))
    }
}

// Chevron from the design SVG (0.3 m wide, 0.5 m long), laid flat on the floor.
private fun chevronPath(camera: Camera, spot: Along): Pair<Path, Float>? {
    // SVG points (x across 0..300, y along 0..500 with 0 = tip) in metres x 1000.
    val svg = listOf(0 to 300, 150 to 0, 300 to 300, 300 to 500, 150 to 200, 0 to 500)
    val right = Point(-spot.direction.y, spot.direction.x) // 90 degrees clockwise on our y-down plan
    val path = Path()
    var zSum = 0f
    svg.forEachIndexed { index, (sx, sy) ->
        val across = (sx - 150) / 1000.0
        val forward = (500 - sy) / 1000.0 - 0.25
        val world = spot.position + spot.direction * forward + right * across
        val (screen, zc) = camera.project(world) ?: return null
        zSum += zc
        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    path.close()
    return path to zSum / svg.size
}

private fun isTurn(kind: StepKind) = kind == StepKind.TURN_LEFT || kind == StepKind.TURN_RIGHT || kind == StepKind.TURN_AROUND

// Larger arrow at the decision point: 0.5 m wide shaft, bent toward the turn.
// Elevator: an up arrow standing at the lobby. Stairs: up or down.
private fun DrawScope.drawNextTurnArrow(camera: Camera, state: GuidanceState, alpha: Float) {
    val step = state.step
    val points = state.route.points
    val node = points.getOrNull(step.startIndex) ?: return
    if (node.floor != state.floor || state.distanceToStepM > 20.0) return

    val worldLine: List<Pair<Point, Double>>
    var head: List<Pair<Point, Double>>
    when {
        isTurn(step.kind) -> {
            val before = points.getOrNull(step.startIndex - 1) ?: return
            val after = points.getOrNull(step.startIndex + 1) ?: return
            val inDir = unit(node.position - before.position) ?: return
            val outDir = unit(after.position - node.position) ?: return
            val start = node.position - inDir * 1.2
            val corner = node.position
            val end = corner + outDir * 1.0
            val side = Point(-outDir.y, outDir.x)
            worldLine = listOf(start to 0.02, corner to 0.02, end to 0.02)
            head = listOf(
                (end + outDir * 0.6) to 0.02,
                (end + side * 0.45) to 0.02,
                (end - side * 0.45) to 0.02,
            )
        }
        step.kind == StepKind.ELEVATOR || step.kind == StepKind.STAIRS -> {
            val up = (step.targetFloor ?: node.floor) >= node.floor
            val base = if (up) 0.3 else 1.6
            val top = if (up) 1.4 else 0.5
            // Stand the arrow in the plane facing the camera: offset "across" uses the view's right vector.
            val across = Point(-sin(camera.heading), cos(camera.heading))
            worldLine = listOf(node.position to base, node.position to top)
            val tipH = if (up) top + 0.45 else top - 0.45
            head = listOf(
                node.position to tipH,
                (node.position + across * 0.4) to top,
                (node.position - across * 0.4) to top,
            )
        }
        else -> return
    }

    val projected = worldLine.map { (p, h) -> camera.project(p, h) ?: return }
    val headProjected = head.map { (p, h) -> camera.project(p, h) ?: return }
    val zc = projected[1.coerceAtMost(projected.lastIndex)].second
    val width = (0.5f * camera.focalPx / zc).coerceIn(10f, 160f)

    val shaft = Path().apply {
        moveTo(projected[0].first.x, projected[0].first.y)
        for (i in 1 until projected.size) lineTo(projected[i].first.x, projected[i].first.y)
    }
    val arrowHead = Path().apply {
        moveTo(headProjected[0].first.x, headProjected[0].first.y)
        lineTo(headProjected[1].first.x, headProjected[1].first.y)
        lineTo(headProjected[2].first.x, headProjected[2].first.y)
        close()
    }
    // Glow, edge, then core (same colors as the chevrons and the banner icon).
    drawPath(shaft, ArOverlayColors.arrowEdge.copy(alpha = 0.3f * alpha), style = Stroke(width * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(shaft, ArOverlayColors.arrowEdge.copy(alpha = alpha), style = Stroke(width + 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(shaft, ArOverlayColors.arrowCore.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(arrowHead, ArOverlayColors.arrowCore.copy(alpha = alpha))
    drawPath(arrowHead, ArOverlayColors.arrowEdge.copy(alpha = alpha), style = Stroke(3f, join = StrokeJoin.Round))
}

// Destination pin: ring 0.4 m across, 1.5 m above the floor at the door, always facing us.
// Arrived: a filled green circle with a dark check, still labelled with the room name.
private fun DrawScope.drawDestinationPin(camera: Camera, state: GuidanceState, textMeasurer: TextMeasurer) {
    val destination = state.destination
    if (destination.floor != state.floor) return

    val anchor = if (state.arrived) {
        // We are standing at the door, so float the pin a few metres ahead on the door side.
        val right = when (state.step.side) {
            Side.RIGHT -> 1.3
            Side.LEFT -> -1.3
            else -> 0.0
        }
        camera.relative(forward = 3.2, right = right)
    } else {
        if (destination.position.distanceTo(camera.user) > 40.0) return
        destination.position
    }

    val (top, zc) = camera.project(anchor, 1.5) ?: return
    val floorPoint = camera.project(anchor, 0.0)?.first
    if (top.x < -100 || top.x > size.width + 100) return
    val radius = (0.2f * camera.focalPx / zc).coerceIn(8f, 64f) * if (state.arrived) 1.5f else 1f

    // Stem from the floor up to the pin.
    if (floorPoint != null) {
        drawLine(
            if (state.arrived) ArOverlayColors.arrived else ArOverlayColors.destination,
            start = floorPoint,
            end = Offset(top.x, top.y + radius),
            strokeWidth = 2.5.dp.toPx(),
        )
    }

    if (state.arrived) {
        drawCircle(ArOverlayColors.arrived.copy(alpha = 0.35f), radius = radius * 1.35f, center = top)
        drawCircle(ArOverlayColors.arrived, radius = radius, center = top)
        val check = Path().apply {
            moveTo(top.x - radius * 0.45f, top.y)
            lineTo(top.x - radius * 0.1f, top.y + radius * 0.35f)
            lineTo(top.x + radius * 0.5f, top.y - radius * 0.35f)
        }
        drawPath(check, ArOverlayColors.onArrived, style = Stroke(radius * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    } else {
        drawCircle(ArOverlayColors.destinationRing.copy(alpha = 0.3f), radius = radius + 6f, center = top, style = Stroke(10f))
        drawCircle(ArOverlayColors.destinationRing, radius = radius, center = top, style = Stroke((radius * 0.22f).coerceAtLeast(3f)))
        drawCircle(ArOverlayColors.destination, radius = radius * 0.35f, center = top)
    }

    // Label pill above the pin: destination fill, dark text.
    val label = textMeasurer.measure(
        destination.name,
        style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF313131)),
    )
    val padX = 14.dp.toPx()
    val pillH = 30.dp.toPx()
    val pillW = label.size.width + padX * 2
    val pillTop = top.y - radius - 10.dp.toPx() - pillH
    drawRoundRect(
        ArOverlayColors.destination,
        topLeft = Offset(top.x - pillW / 2, pillTop),
        size = androidx.compose.ui.geometry.Size(pillW, pillH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillH / 2),
    )
    drawText(label, topLeft = Offset(top.x - label.size.width / 2f, pillTop + (pillH - label.size.height) / 2f))
}

private fun unit(p: Point): Point? {
    val length = hypot(p.x, p.y)
    return if (length < 1e-6) null else Point(p.x / length, p.y / length)
}
