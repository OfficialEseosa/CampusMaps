package com.campusmaps.glasses

import android.content.Context
import android.net.Uri
import android.util.Log
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitConfig
import com.meta.wearable.dat.mockdevice.api.MockRaybanMeta
import java.io.File

// Debug replay: the toolkit's MockDeviceKit pairs a mock Ray-Ban Meta that is registered, powered on,
// unfolded and worn. Its video feed is 6 s of the CS survey walk (CS-W05, H.265 504x896) and each
// capturePhoto() returns a survey photo of a CS sign (assets/glasses-replay/CS, debug source set only).
//
// Which photos: the anchor the route expects next ([hintAnchorId]) if we have its photos, otherwise the
// next anchor in the playlist. A burst of 3 returns that anchor's angle, far and straight shots, which
// is what three glasses stills of one sign at walking pace look like.
class MockReplay(private val context: Context, private val dir: String = "glasses-replay/CS") {
    private var device: MockRaybanMeta? = null
    private var playlistIndex = 0
    private var burstAnchor: String? = null

    val anchorIds: List<String> by lazy {
        context.assets.list(dir).orEmpty().filter { it.endsWith(".jpg") }.map { it.substringBefore("-angle").substringBefore("-far").substringBefore("-straight") }.distinct().sorted()
    }

    fun setUp() {
        val kit = MockDeviceKit.getInstance(context)
        kit.enable(MockDeviceKitConfig(true, true)) // Registered, permissions granted
        val d = kit.pairRaybanMeta()
        d.powerOn()
        d.unfold()
        d.don()
        copy("feed.mp4")?.let { d.services.camera.setCameraFeed(it) }
        device = d
        Log.i(TAG, "mock Ray-Ban Meta paired and worn; replay anchors $anchorIds")
    }

    fun beforeCapture(hintAnchorId: String?, index: Int) {
        val d = device ?: return
        if (index == 0) {
            burstAnchor = if (hintAnchorId != null && hintAnchorId in anchorIds) hintAnchorId
            else anchorIds.getOrNull(playlistIndex++ % anchorIds.size.coerceAtLeast(1))
        }
        val a = burstAnchor ?: return
        val shot = SHOTS[index % SHOTS.size]
        val uri = copy("$a-$shot.jpg") ?: return
        d.services.camera.setCapturedImage(uri)
        Log.i(TAG, "REPLAY still ${index + 1}: $a-$shot.jpg (hint ${hintAnchorId ?: "none"})")
    }

    private fun copy(name: String): Uri? = runCatching {
        val out = File(context.cacheDir, "glasses-replay/$name")
        if (!out.exists()) {
            out.parentFile?.mkdirs()
            context.assets.open("$dir/$name").use { i -> out.outputStream().use { i.copyTo(it) } }
        }
        Uri.fromFile(out)
    }.onFailure { Log.w(TAG, "replay asset $name: $it") }.getOrNull()

    companion object {
        const val TAG = "GlassesReplay"
        private val SHOTS = listOf("angle", "far", "straight")
    }
}
