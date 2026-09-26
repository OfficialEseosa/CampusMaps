package com.campusmaps.glasses

import android.content.Context
import android.util.Log
import com.campusmaps.BuildConfig
import com.campusmaps.data.AnchorKind
import com.campusmaps.data.BuildingLoader
import com.campusmaps.guidance.GlassesLink
import com.campusmaps.guidance.SimulatedGlassesLink
import com.campusmaps.platform.Speaker
import kotlinx.coroutines.CoroutineScope

// Picks the glasses link at app start.
//
//   adb shell setprop debug.campusmaps.glasses replay   mock Ray-Ban Meta replaying CS survey photos (debug builds)
//   adb shell setprop debug.campusmaps.glasses real     the real glasses (Meta toolkit 0.7.0)
//   adb shell setprop debug.campusmaps.glasses sim      the old SimulatedGlassesLink (no camera, no OCR)
//   (unset)                                             real if the Meta AI app is installed, else sim
//
// Then force-stop and reopen the app. The property is lost on reboot.
object GlassesMode {
    const val PROP = "debug.campusmaps.glasses"
    const val TAG = "GlassesMode"
    private const val META_AI_APP = "com.facebook.stella"

    fun create(context: Context, scope: CoroutineScope, speaker: Speaker): GlassesLink {
        val app = context.applicationContext
        val prop = sysprop(PROP)
        val mode = when {
            prop == "replay" && BuildConfig.DEBUG -> "replay"
            prop == "real" || prop == "sim" -> prop
            hasMetaAiApp(app) -> "real"
            else -> "sim"
        }
        Log.i(TAG, "glasses mode $mode (property '$prop')")
        return when (mode) {
            "sim" -> SimulatedGlassesLink(scope, speaker)
            else -> RealGlassesLink(
                scope = scope,
                speaker = speaker,
                source = MetaStillSource(app, replay = if (mode == "replay") MockReplay(app) else null),
                anchorsByBuilding = loadAnchors(app),
            )
        }
    }

    // Text anchors (text plus OCR aliases) of every building file, by building code.
    fun loadAnchors(context: Context): Map<String, List<AnchorText>> {
        val out = linkedMapOf<String, List<AnchorText>>()
        for (f in context.assets.list("buildings").orEmpty().filter { it.endsWith(".json") }) {
            runCatching {
                val b = context.assets.open("buildings/$f").bufferedReader().use { BuildingLoader.fromJson(it.readText()) }
                // Image anchors with a text line (CLASSROOM SOUTH) are readable too.
                out[b.code] = b.anchors.filter { it.text != null && (it.kind == AnchorKind.TEXT || it.kind == AnchorKind.IMAGE) }
                    .map { AnchorText(it.id, it.node, it.floor, it.text!!, it.aliases) }
            }.onFailure { Log.w(TAG, "anchors from $f: $it") }
        }
        Log.i(TAG, "text anchors: ${out.mapValues { it.value.size }}")
        return out
    }

    private fun hasMetaAiApp(context: Context) =
        runCatching { context.packageManager.getPackageInfo(META_AI_APP, 0); true }.getOrDefault(false)

    private fun sysprop(key: String): String? = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        (c.getMethod("get", String::class.java).invoke(null, key) as String).ifBlank { null }
    }.getOrNull()
}
