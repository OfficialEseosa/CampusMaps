package com.campusmaps.glasses

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.SystemClock
import android.util.Log
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addStream
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamError
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream

// Stills from the Ray-Ban Meta glasses through the Meta Wearables Device Access Toolkit 0.7.0
// (API shapes from tools/meta-sample/sample-0.7.0/samples/CameraAccess).
//
// One burst = session start -> addStream -> STREAMING -> capturePhoto() x N -> stream stop -> session stop.
// The session is stopped too (not only the stream) so nothing holds the glasses' camera while the phone
// speaks: the real glasses dropped the stream when audio started (docs/06, "Verified on the real glasses").
// With [replay] set, the toolkit's MockDeviceKit plays a mock Ray-Ban Meta instead (debug builds).
@SuppressLint("AutoCloseableUse")
class MetaStillSource(
    private val context: Context,
    private val replay: MockReplay? = null,
) : StillSource {

    override val name = if (replay != null) "mock glasses (replay)" else "Ray-Ban Meta"

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var initialised = false
    private var lastSetupLaunch = 0L
    private var session: DeviceSession? = null
    private var stream: Stream? = null
    private var watchJobs = mutableListOf<Job>()
    @Volatile private var failure: String? = null

    private val selector by lazy { AutoDeviceSelector() }

    override suspend fun connect(): Boolean {
        if (!initialised) {
            var ok = true
            Wearables.initialize(context).onFailure { e, _ -> ok = false; Log.e(TAG, "initialize failed: ${e.description}") }
            if (!ok) return false
            initialised = true
            Log.i(TAG, "Wearables initialised, dev mode ${Wearables.isDevMode}")
            replay?.setUp()
        }
        val reg = Wearables.registrationState.value
        if (reg != RegistrationState.REGISTERED) {
            Log.w(TAG, "registration state $reg")
            askForSetup("registration $reg")
            return false.also { _connected.value = false }
        }
        var camera: PermissionStatus? = null
        Wearables.checkPermissionStatus(Permission.CAMERA).onSuccess { camera = it }
        if (camera != PermissionStatus.Granted) {
            Log.w(TAG, "glasses camera permission $camera")
            askForSetup("camera permission")
            return false.also { _connected.value = false }
        }
        val devices = Wearables.devices.value
        if (devices.isEmpty()) {
            Log.w(TAG, "no glasses in range")
            return false.also { _connected.value = false }
        }
        _connected.value = true
        return true
    }

    override suspend fun burst(count: Int, hintAnchorId: String?): List<Bitmap> {
        failure = null
        val t0 = SystemClock.elapsedRealtime()
        val s = Wearables.createSession(selector).let { r ->
            var created: DeviceSession? = null
            r.onSuccess { created = it }.onFailure { e, _ -> throw GlassesUnavailable("createSession: ${e.description}") }
            created!!
        }
        session = s
        watchJobs += scope.launch { s.errors.collect { e -> failure = "session: ${e.description}"; Log.e(TAG, "session error ${e.description}") } }
        try {
            s.start()
            timed(SESSION_TIMEOUT_MS, "session start") {
                s.state.first { it == DeviceSessionState.STARTED || it == DeviceSessionState.STOPPED || failure != null }
            }
            check()
            if (s.state.value != DeviceSessionState.STARTED) throw GlassesUnavailable("session ${s.state.value}")
            Log.i(TAG, "BURST session STARTED in ${SystemClock.elapsedRealtime() - t0} ms")

            var added: Stream? = null
            s.addStream(StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 24))
                .onSuccess { added = it }
                .onFailure { e, _ -> throw GlassesUnavailable("addStream: ${e.description}") }
            val st = added!!
            stream = st
            watchJobs += scope.launch {
                st.errorStream.collect { e ->
                    Log.w(TAG, "stream error ${e.description}")
                    if (e != StreamError.STREAM_ERROR) failure = "stream: ${e.description}"
                }
            }
            watchJobs += scope.launch { st.videoStream.collect { } } // Drain frames; stills are the OCR path
            st.start()
            timed(STREAM_TIMEOUT_MS, "stream start") {
                st.state.first { it == StreamState.STREAMING || it == StreamState.CLOSED || failure != null }
            }
            check()
            if (st.state.value != StreamState.STREAMING) throw GlassesUnavailable("stream ${st.state.value}")
            Log.i(TAG, "BURST camera ON (streaming) at ${SystemClock.elapsedRealtime() - t0} ms")
            // The real glasses need a moment of streaming before a still succeeds (docs/06: 5 s warm-up on the stream).
            if (replay == null) delay(WARMUP_MS)

            val stills = mutableListOf<Bitmap>()
            for (i in 0 until count) {
                replay?.beforeCapture(hintAnchorId, i)
                val c0 = SystemClock.elapsedRealtime()
                var photo: PhotoData? = null
                for (attempt in 1..CAPTURE_ATTEMPTS) {
                    // A timed-out or failed still is skipped, never fatal: the cycle still speaks the instruction.
                    val ok = try {
                        withTimeout(CAPTURE_TIMEOUT_MS) {
                            st.capturePhoto().onSuccess { photo = it }.onFailure { e, _ -> Log.w(TAG, "capturePhoto failed: ${e.description}") }
                        }
                        photo != null
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "capturePhoto timed out after $CAPTURE_TIMEOUT_MS ms (attempt $attempt)")
                        false
                    }
                    check()
                    if (ok) break
                    if (attempt < CAPTURE_ATTEMPTS) delay(CAPTURE_RETRY_MS)
                }
                if (photo == null && i == 0) { Log.w(TAG, "BURST first still failed twice; skipping the rest of this burst"); break }
                photo?.let(::decode)?.let {
                    stills += it
                    Log.i(TAG, "BURST still ${i + 1}/$count ${it.width}x${it.height} in ${SystemClock.elapsedRealtime() - c0} ms")
                }
            }
            if (stills.isEmpty()) Log.w(TAG, "BURST no stills this cycle")
            return stills
        } finally {
            stopCamera()
            Log.i(TAG, "BURST camera OFF (stream and session stopped) at ${SystemClock.elapsedRealtime() - t0} ms")
        }
    }

    // withTimeout throws a CancellationException subclass; as a plain failure it would silently kill the cycle loop.
    private suspend fun <T> timed(ms: Long, what: String, block: suspend () -> T): T =
        try { withTimeout(ms) { block() } } catch (e: TimeoutCancellationException) { throw GlassesUnavailable("$what timed out after $ms ms") }

    private fun check() {
        failure?.let { _connected.value = false; throw GlassesUnavailable(it) }
    }

    private fun stopCamera() {
        watchJobs.forEach { it.cancel() }
        watchJobs.clear()
        runCatching { stream?.stop() }.onFailure { Log.w(TAG, "stream stop: $it") }
        stream = null
        runCatching { session?.stop() }.onFailure { Log.w(TAG, "session stop: $it") }
        session = null
    }

    override fun disconnect() {
        stopCamera()
        _connected.value = false
    }

    // Registration and the glasses camera permission need an Activity (the Meta AI app opens and returns).
    private fun askForSetup(why: String) {
        if (replay != null) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSetupLaunch < SETUP_RETRY_MS && lastSetupLaunch != 0L) return
        lastSetupLaunch = now
        Log.i(TAG, "opening glasses setup: $why")
        context.startActivity(Intent(context, GlassesSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun decode(photo: PhotoData): Bitmap? = when (photo) {
        is PhotoData.Bitmap -> photo.bitmap
        is PhotoData.HEIC -> {
            val bytes = ByteArray(photo.data.remaining()).also { photo.data.get(it) }
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            raw?.let { upright(it, bytes) }
        }
        else -> null
    }

    // ML Kit reads nothing on a sideways image: apply the EXIF orientation.
    private fun upright(bmp: Bitmap, bytes: ByteArray): Bitmap {
        val o = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val deg = when (o) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
    }

    companion object {
        const val TAG = "GlassesMeta"
        const val SESSION_TIMEOUT_MS = 20_000L
        const val STREAM_TIMEOUT_MS = 15_000L
        const val CAPTURE_TIMEOUT_MS = 8_000L
        const val CAPTURE_ATTEMPTS = 2
        const val CAPTURE_RETRY_MS = 700L
        const val WARMUP_MS = 1_500L
        const val SETUP_RETRY_MS = 60_000L
    }
}
