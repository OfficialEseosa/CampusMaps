package com.campusmaps.ui.ar

import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campusmaps.loc.AnchorImages
import com.campusmaps.loc.ArFeed
import com.campusmaps.loc.ArTracking
import com.campusmaps.loc.BuildingToWorld
import com.campusmaps.loc.CameraSample
import com.campusmaps.loc.ImageFix
import com.campusmaps.loc.Vec3
import androidx.compose.ui.platform.LocalContext
import com.google.ar.core.Anchor
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import kotlin.math.hypot

private const val TAG = "ArGuidance"

/** Arrows beyond this are hidden (docs/05: the ribbon must not visibly cross walls). */
private const val HIDE_BEYOND_M = 15.0
/** Arrows between this and [HIDE_BEYOND_M] use the dim material. */
private const val DIM_BEYOND_M = 10.0
private const val DEST_LABEL_MAX_M = 40.0

/**
 * Flat chevron, 0.3 m wide and 0.5 m long, drawn in ShapeNode's XY plane pointing +Y. The ShapeNode is rotated -90 degrees
 * about X, which lays it on the floor pointing local -Z with its face up; the parent node's yaw ([FloorArrow.yawDeg])
 * turns it along the route. Counter-clockwise order.
 */
private val CHEVRON = listOf(
    Float2(0f, 0.25f), Float2(-0.15f, -0.05f), Float2(-0.15f, -0.25f),
    Float2(0f, -0.05f), Float2(0.15f, -0.25f), Float2(0.15f, -0.05f),
)

/** Mutable per-frame data that must not trigger recomposition. */
private class FrameBox {
    var pendingTap: Offset? = null
    var camWorld: Float3 = Float3(0f)
    var frameCount = 0
    var anchor: Anchor? = null
    /** Anchor translation at the moment of placement; drift of the anchor from this shifts the transform. */
    var anchorRef: Vec3? = null
    var lastAnchorCheckMs = 0L
    /** Last sign fix per anchor id (ms), to re-snap at most every [IMAGE_FIX_EVERY_MS]. */
    val lastImageFix = HashMap<String, Long>()
}

private const val IMAGE_FIX_EVERY_MS = 1500L

/**
 * S2's AR slot (docs/05): SceneView ARScene with the route drawn on the real floor, plus the debug "Place route here"
 * flow that fixes the building->world transform from one floor tap and the phone's heading (yaw-only, docs/03 section 1).
 *
 * Self-contained: it knows only [ArRouteInput] (building-frame route, floor, turn, destination, placement) and the
 * transform, which the host owns ([buildingToWorld] in, [onBuildingToWorld] out) so a real localizer can set it too.
 * The host must compose this only when ARCore reports SUPPORTED_* and CAMERA is granted.
 */
@Composable
fun ArGuidanceView(
    input: ArRouteInput?,
    buildingToWorld: BuildingToWorld?,
    onBuildingToWorld: (BuildingToWorld?) -> Unit,
    onFail: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Distance of the hint / "Place route here" column from the bottom, so the host can keep it above its minimap. */
    hintBottom: androidx.compose.ui.unit.Dp = 220.dp,
    /** False hides the hint column (host shows its own prompt, e.g. Locate me or the arrived buttons). */
    showHints: Boolean = true,
    /** Outdoor leg (w1/geo): feeds this session to the Geospatial provider and draws its chevrons toward the entrance. */
    outdoor: com.campusmaps.geo.ArCoreGeospatialProvider? = null,
) {
    val latestOutdoor by rememberUpdatedState(outdoor)
    val box = remember { FrameBox() }
    val context = LocalContext.current
    var tracking by remember { mutableStateOf(TrackingState.PAUSED) }
    var failure by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var floorSeen by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    /** Camera position for distance culling, updated only when it moves 0.3 m (limits recomposition). */
    var camForCull by remember { mutableStateOf<Vec3?>(null) }

    // The world frame belongs to this ARCore session: forget the alignment when the AR view leaves.
    DisposableEffect(Unit) {
        onDispose {
            box.anchor?.detach(); box.anchor = null
            ArFeed.viewGone()
            onBuildingToWorld(null)
        }
    }

    // 300 ms slide on re-snap (docs/05 "Smoothing"); the first fix appears at once.
    val target = buildingToWorld
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val from = shown
        if (target == null || from == null) { shown = target; return@LaunchedEffect }
        animate(0f, 1f, animationSpec = tween(300)) { v, _ -> shown = from.lerp(target, v.toDouble()) }
        shown = target
    }

    // ARScene may keep the first onSessionUpdated lambda: read inputs through these.
    val latestInput by rememberUpdatedState(input)
    val latestT by rememberUpdatedState(buildingToWorld)
    val setT by rememberUpdatedState(onBuildingToWorld)

    fun onFrame(frame: Frame) {
        val cam = frame.camera
        if (cam.trackingState != tracking) {
            tracking = cam.trackingState
            Log.i(TAG, "tracking $tracking ${cam.trackingFailureReason}")
        }
        sendSample(frame)
        if (cam.trackingState != TrackingState.TRACKING) return
        val p = cam.pose
        box.camWorld = Float3(p.tx(), p.ty(), p.tz())
        val c = camForCull
        if (c == null || hypot(c.x - p.tx(), c.z - p.tz()) > 0.3) camForCull = Vec3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble())

        if (!floorSeen && box.frameCount++ % 15 == 0) {
            floorSeen = frame.getUpdatedTrackables(Plane::class.java).any {
                it.trackingState == TrackingState.TRACKING && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
            }
        }

        box.pendingTap?.let { tap ->
            box.pendingTap = null
            placeAt(frame, tap, latestInput, setT, box)?.let { msg -> message = msg; armed = msg.startsWith("No floor") }
        }

        // Sign snap: a tracked anchor image fixes the whole transform (docs/03 section 1).
        for (img in frame.getUpdatedTrackables(AugmentedImage::class.java)) {
            if (img.trackingState != TrackingState.TRACKING || img.trackingMethod != AugmentedImage.TrackingMethod.FULL_TRACKING) continue
            val nowMs = System.currentTimeMillis()
            if (nowMs - (box.lastImageFix[img.name] ?: 0L) < IMAGE_FIX_EVERY_MS) continue
            box.lastImageFix[img.name] = nowMs
            val anchor = ArFeed.anchors[img.name]
            if (anchor == null) { Log.w(TAG, "image ${img.name} is not an anchor of this building"); continue }
            val cp = img.centerPose
            val n = cp.yAxis
            val t = ImageFix.transform(anchor, cp.tx().toDouble(), cp.ty().toDouble(), cp.tz().toDouble(), n[0].toDouble(), n[2].toDouble(), ArFeed.floorHeightM)
            if (t == null) { Log.w(TAG, "image ${img.name}: not on a wall or no facing; ignored"); continue }
            box.anchor?.detach()
            box.anchor = img.createAnchor(cp)
            box.anchorRef = Vec3(cp.tx().toDouble(), cp.ty().toDouble(), cp.tz().toDouble())
            setT(t)
            message = "Located from sign ${anchor.id}"
            Log.i(TAG, "sign fix ${anchor.id} floor ${anchor.floor}: centre=(%.2f, %.2f, %.2f) yaw=%.1f deg".format(cp.tx(), cp.ty(), cp.tz(), t.yawDeg))
        }

        // Follow ARCore's corrections to the anchor (translation only; yaw stays from the tap).
        val a = box.anchor; val ref = box.anchorRef; val now = System.currentTimeMillis()
        if (a != null && ref != null && a.trackingState == TrackingState.TRACKING && now - box.lastAnchorCheckMs > 1000) {
            box.lastAnchorCheckMs = now
            val ap = a.pose
            val dx = ap.tx() - ref.x; val dy = ap.ty() - ref.y; val dz = ap.tz() - ref.z
            val t = latestT
            if (t != null && hypot(hypot(dx, dy), dz) > 0.03) {
                box.anchorRef = Vec3(ap.tx().toDouble(), ap.ty().toDouble(), ap.tz().toDouble())
                setT(t.copy(tx = t.tx + dx, ty = t.ty + dy, tz = t.tz + dz))
                Log.i(TAG, "anchor moved %.3f m; transform shifted".format(hypot(hypot(dx, dy), dz)))
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = shown == null,
            sessionConfiguration = { session, config ->
                AnchorImages.load(context, session)?.let { config.augmentedImageDatabase = it }
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.focusMode = Config.FocusMode.AUTO
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                config.depthMode = Config.DepthMode.DISABLED
                latestOutdoor?.configure(session, config)
            },
            onSessionUpdated = { session, frame -> onFrame(frame); latestOutdoor?.onFrame(session, frame) },
            onTrackingFailureChanged = { failure = it },
            onSessionFailed = { e -> Log.e(TAG, "AR session failed", e); onFail(e.message ?: e.javaClass.simpleName) },
        ) {
            val cyan = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFF00E5FF)) }
            val dim = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFF00707D)) }
            val amber = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFFFFB300)) }
            val red = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFFEF5350)) }

            outdoor?.let { OutdoorArrowLayer(it) }

            val t = shown
            if (t != null && input != null && tracking == TrackingState.TRACKING) {
                val floor = input.floor
                val turn = input.nextTurn
                val arrows = remember(input.points, floor, turn) { RouteArrows.chain(input.points, floor, clearAround = turn?.let { it.x to it.y }) }
                val camB = camForCull?.let { t.toBuilding(it) }
                fun dist(x: Double, y: Double) = camB?.let { hypot(x - it.first, y - it.second) } ?: 0.0
                val dy = (floor - t.refFloor) * t.floorHeightM

                // One root node whose pose is buildingToWorld; children are in building-local (x, h, -y).
                Node(position = Float3(t.tx.toFloat(), t.ty.toFloat(), t.tz.toFloat()), rotation = Float3(0f, t.yawDeg.toFloat(), 0f)) {
                    arrows.forEachIndexed { i, a ->
                        val d = dist(a.x, a.y)
                        if (d <= HIDE_BEYOND_M) key(i) {
                            Node(position = Float3(a.x.toFloat(), (dy + 0.02).toFloat(), (-a.y).toFloat()), rotation = Float3(0f, a.yawDeg.toFloat(), 0f)) {
                                ShapeNode(polygonPath = CHEVRON, materialInstance = if (d > DIM_BEYOND_M) dim else cyan, rotation = Float3(-90f, 0f, 0f))
                            }
                        }
                    }
                    if (turn != null && dist(turn.x, turn.y) <= HIDE_BEYOND_M) {
                        Node(position = Float3(turn.x.toFloat(), (dy + 0.04).toFloat(), (-turn.y).toFloat()), rotation = Float3(0f, turn.yawDeg.toFloat(), 0f), scale = Float3(2.2f)) {
                            ShapeNode(polygonPath = CHEVRON, materialInstance = amber, rotation = Float3(-90f, 0f, 0f))
                        }
                    }
                    val dest = input.destination
                    if (dest != null && dest.floor == floor && dist(dest.x, dest.y) <= DEST_LABEL_MAX_M) {
                        CubeNode(size = Float3(0.05f, 1.3f, 0.05f), materialInstance = red,
                            position = Float3(dest.x.toFloat(), (dy + 0.65).toFloat(), (-dest.y).toFloat()))
                        TextNode(
                            text = dest.label,
                            fontSize = 64f,
                            textColor = Color.White.toArgb(),
                            backgroundColor = Color(0xE6C62828).toArgb(),
                            widthMeters = 1.0f, heightMeters = 0.28f,
                            position = Float3(dest.x.toFloat(), (dy + 1.5).toFloat(), (-dest.y).toFloat()),
                            cameraPositionProvider = { box.camWorld },
                        )
                    }
                }
            }
        }

        // Debug place flow: while armed, a transparent layer catches the floor tap (overlays above it still get theirs).
        if (armed) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { box.pendingTap = it } })
        }

        val placement = input?.placement

        if (showHints) Column(
            Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = hintBottom),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val prompt = when {
                tracking != TrackingState.TRACKING -> "Point the camera at the floor or a sign" + (failure?.takeIf { it != TrackingFailureReason.NONE }?.let { "\n(${reasonText(it)})" } ?: "")
                armed -> "Stand at ${placement?.label ?: "your node"}, face along the route, and tap the floor just in front of you"
                target == null && !floorSeen -> "Move the phone slowly over the floor to find it"
                else -> null
            }
            (prompt ?: message)?.let { Hint(it) }
            if (tracking == TrackingState.TRACKING && placement != null) {
                if (target == null && !armed) Button(onClick = { armed = true; message = null }) { Text("Place route here") }
                else if (armed) OutlinedButton(onClick = { armed = false }) { Text("Cancel") }
                else OutlinedButton(onClick = { armed = true; message = null }) { Text("Re-place route") }
            }
        }
    }
}

/** Camera pose for ArPositionProvider (through ArFeed). Heading as in [placeAt]: forward, or screen-up when looking down. */
private fun sendSample(frame: Frame) {
    val cam = frame.camera
    val tr = when (cam.trackingState) {
        TrackingState.TRACKING -> ArTracking.TRACKING
        TrackingState.PAUSED -> ArTracking.PAUSED
        else -> ArTracking.STOPPED
    }
    val cp = cam.displayOrientedPose
    val z = cp.zAxis; val y = cp.yAxis
    var fx = -z[0].toDouble(); var fz = -z[2].toDouble()
    if (hypot(fx, fz) < 0.3) { fx = y[0].toDouble(); fz = y[2].toDouble() }
    ArFeed.sample(CameraSample(tr, cp.tx().toDouble(), cp.ty().toDouble(), cp.tz().toDouble(), fx, fz, System.currentTimeMillis()))
}

/** Hit-tests the tap on an upward floor plane and sets the transform. Returns a user message. */
private fun placeAt(frame: Frame, tap: Offset, input: ArRouteInput?, setT: (BuildingToWorld?) -> Unit, box: FrameBox): String? {
    val pl = input?.placement ?: return "No route edge to place"
    val hit = frame.hitTest(tap.x, tap.y).firstOrNull { h ->
        val tr = h.trackable
        tr is Plane && tr.type == Plane.Type.HORIZONTAL_UPWARD_FACING && tr.trackingState == TrackingState.TRACKING && tr.isPoseInPolygon(h.hitPose)
    } ?: return "No floor there yet. Move the phone slowly over the floor, then tap again."

    // Phone heading: camera forward (-Z of the display-oriented pose), flattened. Looking straight down, use screen-up.
    val cp = frame.camera.displayOrientedPose
    val z = cp.zAxis; val y = cp.yAxis
    var fx = -z[0].toDouble(); var fz = -z[2].toDouble()
    if (hypot(fx, fz) < 0.3) { fx = y[0].toDouble(); fz = y[2].toDouble() }

    val hp = hit.hitPose
    val world = Vec3(hp.tx().toDouble(), hp.ty().toDouble(), hp.tz().toDouble())
    val t = BuildingToWorld.fromCorrespondence(world, fx, fz, pl.x, pl.y, pl.dirX, pl.dirY, pl.floor, input.floorHeightM)
        ?: return "Could not read the phone heading; try again"
    box.anchor?.detach()
    box.anchor = hit.createAnchor()
    box.anchorRef = world
    setT(t)
    Log.i(TAG, "placed at ${pl.label} floor ${pl.floor}: hit=$world heading=(%.2f, %.2f) yaw=%.1f deg".format(fx, fz, t.yawDeg))
    return "Route placed at ${pl.label}"
}

private fun reasonText(r: TrackingFailureReason) = when (r) {
    TrackingFailureReason.INSUFFICIENT_LIGHT -> "too dark"
    TrackingFailureReason.EXCESSIVE_MOTION -> "moving too fast"
    TrackingFailureReason.INSUFFICIENT_FEATURES -> "point at something with more texture"
    TrackingFailureReason.CAMERA_UNAVAILABLE -> "camera unavailable"
    TrackingFailureReason.BAD_STATE -> "restarting"
    else -> r.name.lowercase()
}

@Composable
private fun Hint(text: String) {
    Surface(color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f), shape = MaterialTheme.shapes.medium) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.inverseOnSurface,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
