package com.campusmaps.platform

import android.content.Context
import android.util.Log
import com.campusmaps.BuildConfig
import java.io.File

/**
 * Debug builds keep their own copy of the guidance log on the phone, because ARCore's native code writes thousands of
 * lines a second and Samsung caps the Logcat buffer at 5 MB, so the app's lines age out within a minute.
 * A background `logcat` of the app's own tags (allowed for an app's own process) is appended to
 * filesDir/logs/campusmaps.log; the file is rotated at 4 MB (one previous copy kept). Pull it with
 * `adb shell run-as com.campusmaps cat files/logs/campusmaps.log`.
 */
object LogSink {
    private const val TAG = "LogSink"
    private const val MAX_BYTES = 4L * 1024 * 1024
    private val TAGS = listOf(
        "Geo", "Handoff", "ArGuidanceView", "ArFeed", "GuidanceController", "Baro", "WatchBridge", "GlassesLink",
        "GlassesMeta", "GlassesMode", "Speaker", "FindMe", "Directions", "GpsStart", "BuildingRepository", "LogSink",
        "AndroidRuntime:E",
    )
    @Volatile private var started = false

    fun start(context: Context) {
        if (!BuildConfig.DEBUG || started) return
        started = true
        val dir = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
        val file = File(dir, "campusmaps.log")
        if (file.length() > MAX_BYTES) file.renameTo(File(dir, "campusmaps.1.log"))
        Thread({
            try {
                val cmd = mutableListOf("logcat", "-v", "time", "-T", "1", "-s") + TAGS
                val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
                Log.i(TAG, "logging ${TAGS.size} tags to ${file.path}")
                file.appendText("\n==== app start ${java.util.Date()} ====\n")
                p.inputStream.bufferedReader().useLines { lines ->
                    java.io.FileWriter(file, true).buffered().use { w ->
                        for (line in lines) {
                            w.write(line); w.newLine(); w.flush()
                            if (file.length() > MAX_BYTES) { w.flush(); break }
                        }
                    }
                }
                p.destroy()
                Log.w(TAG, "log file full (${MAX_BYTES / 1024 / 1024} MB); restart the app to rotate")
            } catch (e: Exception) {
                Log.w(TAG, "log sink stopped: $e")
            }
        }, "campusmaps-logsink").apply { isDaemon = true }.start()
    }
}
