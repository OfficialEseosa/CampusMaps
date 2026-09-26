package com.campusmaps.ui.ar

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

// Live back camera image, full screen. This is what sits behind the S2 overlay.
// When the 3D team's ARCore session is ready, it replaces this composable (same place in the tree).
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            } catch (e: Exception) {
                Log.w("CameraPreview", "Camera not available: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

// Stand-in for the camera image when there is no camera permission (debug "AR forced on").
// A simple painted hallway, the same one used in the design canvas.
@Composable
fun PaintedHallway(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // The design was drawn on a 412 x 915 frame; scale every point from that.
        fun p(x: Float, y: Float) = Offset(x / 412f * w, y / 915f * h)
        drawRect(Color(0xFF2A2420))
        fun poly(color: Color, pts: List<Offset>) {
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            drawPath(path, color)
        }
        poly(Color(0xFF3A322D), listOf(p(0f, 915f), p(412f, 915f), p(206f, 360f)))   // Floor
        poly(Color(0xFF221D1A), listOf(p(0f, 150f), p(412f, 150f), p(206f, 360f)))   // Ceiling
        val edge = Color(0xFF51473F)
        drawLine(edge, p(206f, 360f), p(0f, 150f), 2f)
        drawLine(edge, p(206f, 360f), p(412f, 150f), 2f)
        drawLine(edge, p(206f, 360f), p(0f, 915f), 2f)
        drawLine(edge, p(206f, 360f), p(412f, 915f), 2f)
        val frame = Color(0xFF463D37)
        drawLine(frame, p(60f, 211f), p(60f, 753f), 2f)
        drawLine(frame, p(130f, 283f), p(130f, 565f), 2f)
        drawLine(frame, p(352f, 211f), p(352f, 753f), 2f)
        drawLine(frame, p(282f, 283f), p(282f, 565f), 2f)
        // Ceiling lights getting smaller into the distance.
        val light = Color(0xFF6B5F57)
        listOf(Triple(200f, 70f, 7f), Triple(265f, 42f, 4.2f), Triple(305f, 24f, 3f), Triple(330f, 14f, 3f)).forEach { (y, width, height) ->
            drawRect(light, topLeft = p(206f - width / 2, y), size = androidx.compose.ui.geometry.Size(width / 412f * w, height / 915f * h))
        }
        drawRect(Color(0xFF5A4B41), topLeft = p(193f, 338f), size = androidx.compose.ui.geometry.Size(26f / 412f * w, 36f / 915f * h))
        poly(Color(0xFF4A4038), listOf(p(118f, 270f), p(150f, 303f), p(150f, 511f), p(118f, 597f))) // A door on the left
    }
}
