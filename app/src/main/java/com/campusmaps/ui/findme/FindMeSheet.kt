package com.campusmaps.ui.findme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campusmaps.BuildConfig
import com.campusmaps.data.model.Building
import com.campusmaps.glasses.SignReader
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.LocalCampusPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val TAG = "FindMe"
const val FRAME_EVERY_MS = 700L
const val AUTO_ACCEPT_MS = 1500L
const val NO_MATCH_AFTER_MS = 20_000L

// Debug builds only: `adb shell setprop debug.campusmaps.findme anchors/KL/KL-A03.jpg` makes the sheet read that
// asset photo every 700 ms instead of camera frames (the emulator camera cannot see a real sign). Empty = camera.
const val DEBUG_STILL_PROP = "debug.campusmaps.findme"

// Full-screen camera sheet behind S1's "Find me": reads sign text every 700 ms (ML Kit, SignReader), votes against
// [building]'s anchors and room numbers (FindMeVoter) and calls [onResult] with the node id. [onCancel] closes it.
@Composable
fun FindMeSheet(building: Building, onResult: (FindMeMatch) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalCampusPalette.current
    BackHandler(onBack = onCancel)

    var granted by remember { mutableStateOf(hasCamera(context)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    val debugStill = remember { if (BuildConfig.DEBUG) debugStill(context) else null }
    val voter = remember(building.id) { FindMeVoter.forBuilding(building) }
    val frames = remember { Channel<Bitmap>(Channel.CONFLATED) }
    var seen by remember { mutableStateOf("") }
    var match by remember { mutableStateOf<FindMeMatch?>(null) }
    var noMatch by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val accept: (FindMeMatch) -> Unit = { m -> if (!done) { done = true; onResult(m) } }

    // OCR and vote, one frame at a time, off the main thread. Stops at the first match.
    LaunchedEffect(granted, voter) {
        if (!granted) return@LaunchedEffect
        val reader = SignReader()
        try {
            while (isActive && match == null) {
                val bitmap = frames.receive()
                val text = runCatching { withContext(Dispatchers.Default) { FindMeOcr.read(reader, bitmap) } }
                    .onFailure { Log.w(TAG, "OCR failed: $it") }.getOrDefault("")
                if (text.isNotBlank()) seen = text.lines().filter { it.isNotBlank() }.joinToString("  ")
                val m = withContext(Dispatchers.Default) { voter.onFrame(text) }
                Log.i(TAG, "frame text '${text.replace('\n', '|')}' -> ${m?.nodeId ?: "no match"}")
                if (m != null) match = m
            }
        } finally {
            reader.close()
        }
    }
    // Debug still: the same photo every 700 ms.
    LaunchedEffect(granted, debugStill) {
        if (!granted || debugStill == null) return@LaunchedEffect
        while (isActive) {
            frames.trySend(debugStill)
            delay(FRAME_EVERY_MS)
        }
    }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        delay(NO_MATCH_AFTER_MS)
        if (match == null) noMatch = true
    }
    LaunchedEffect(match) {
        val m = match ?: return@LaunchedEffect
        delay(AUTO_ACCEPT_MS)
        accept(m)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } } // Swallow taps meant for S1 underneath (no semantics merge)
            .testTag("findMeSheet"),
    ) {
        if (granted) {
            if (debugStill != null) {
                Image(debugStill.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                AnalysingCamera(onFrame = { frames.trySend(it) })
            }
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(palette.surface.copy(alpha = 0.85f))
                .align(Alignment.TopStart),
        ) {
            Icon(AppIcons.close, contentDescription = "Cancel", tint = palette.ink)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(palette.surface)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!granted) {
                CameraNeeded(asked = asked, onAsk = { launcher.launch(Manifest.permission.CAMERA) }, onCancel = onCancel)
            } else {
                Text(
                    "Point at a room number or a sign",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink,
                )
                Text(
                    if (seen.isBlank()) "Looking for text..." else "Seen: $seen",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = palette.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("findMeSeen"),
                )
                val m = match
                if (m != null) {
                    val name = building.nodes[m.nodeId]?.name ?: m.nodeId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.soft)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(AppIcons.checkCircle, contentDescription = null, tint = palette.line, modifier = Modifier.size(26.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink,
                            modifier = Modifier.weight(1f).testTag("findMeMatch"),
                        )
                    }
                    PrimaryButton("Use this", Modifier.testTag("findMeUse")) { accept(m) }
                } else if (noMatch) {
                    Text(
                        "No known sign here. Try another sign or pick your start below.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.ink,
                        modifier = Modifier.testTag("findMeNoMatch"),
                    )
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.muted)
                }
            }
        }
    }
}

@Composable
private fun CameraNeeded(asked: Boolean, onAsk: () -> Unit, onCancel: () -> Unit) {
    val palette = LocalCampusPalette.current
    Column(Modifier.testTag("findMeCameraNeeded"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcons.photoCamera, contentDescription = null, tint = palette.line, modifier = Modifier.size(26.dp))
            Text("Camera needed", style = MaterialTheme.typography.titleLarge, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
        }
        Text(
            if (asked) "Find me reads the room number or sign in front of you. Allow the camera, or pick your start below."
            else "Find me reads the room number or sign in front of you.",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            color = palette.muted,
        )
        PrimaryButton("Allow camera", Modifier.testTag("findMeAllow"), onAsk)
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.muted)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalCampusPalette.current
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// Back camera preview plus an analyser that hands over one upright bitmap every FRAME_EVERY_MS.
@Composable
private fun AnalysingCamera(onFrame: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        var last = 0L
        future.addListener({
            try {
                provider = future.get()
                preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setResolutionSelector(
                        ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        ).build(),
                    )
                    .build().also {
                        it.setAnalyzer(executor) { image ->
                            val now = System.currentTimeMillis()
                            if (now - last >= FRAME_EVERY_MS) {
                                last = now
                                runCatching { onFrame(upright(image)) }.onFailure { e -> Log.w(TAG, "frame: $e") }
                            }
                            image.close()
                        }
                    }
                provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Log.w(TAG, "camera failed: $e")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbind(preview, analysis) }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun upright(image: ImageProxy): Bitmap {
    val bitmap = image.toBitmap()
    val degrees = image.imageInfo.rotationDegrees
    if (degrees == 0) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}

private fun hasCamera(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun debugStill(context: Context): Bitmap? {
    val path = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        (c.getMethod("get", String::class.java).invoke(null, DEBUG_STILL_PROP) as String).trim()
    }.getOrNull()
    if (path.isNullOrEmpty()) return null
    return runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }
        .onFailure { Log.w(TAG, "debug still $path: $it") }.getOrNull()
        ?.also { Log.i(TAG, "debug still $path (${it.width}x${it.height})") }
}
