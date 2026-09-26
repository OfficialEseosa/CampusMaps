package com.campusmaps.geo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.campusmaps.BuildConfig
import com.campusmaps.ui.ar.OUTDOOR_GROUND_BELOW_CAMERA_M
import com.campusmaps.ui.ar.OutdoorChain
import com.campusmaps.ui.ar.WorldArrow
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.VpsAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.hypot

/**
 * The outdoor chevrons for the AR view, already gated. Empty when nothing may be drawn. [door] is the entrance's
 * terrain anchor in ARCore world (same gate), for the card door sign.
 */
data class OutdoorArrowFrame(
    val arrows: List<WorldArrow> = emptyList(), val groundY: Float = 0f,
    val door: com.campusmaps.loc.Vec3? = null,
)

/**
 * ARCore 1.56 Geospatial provider (docs/03 section 4). It does not own a session: SceneView's ARScene does. The AR view
 * calls [configure] from `sessionConfiguration` and [onFrame] from `onSessionUpdated` (see ArGuidanceView's `outdoor`
 * parameter). Everything fails soft: no API key, no location permission, no Geospatial support, no network, or no VPS all
 * end in [EarthTracking.UNAVAILABLE] or PAUSED with a [GeoState.failure] line, never an exception.
 *
 * Log tag "Geo": earth state, tracking, accuracy, VPS answer and anchor state are all logged on change.
 */
class ArCoreGeospatialProvider(private val context: Context) : GeospatialProvider {
    private val _state = MutableStateFlow(GeoState())
    override val state: StateFlow<GeoState> = _state.asStateFlow()

    private val _arrows = MutableStateFlow(OutdoorArrowFrame())
    /** Chevrons toward the entrance anchor, only while [OutdoorArrowGate] is open and the anchor is tracked. */
    val arrows: StateFlow<OutdoorArrowFrame> = _arrows.asStateFlow()

    private var running = false
    private var session: Session? = null
    private var pendingVps: LatLng? = null
    private var pendingAnchor: LatLng? = null
    private var anchor: Anchor? = null
    private var lastPublishMs = 0L
    private var lastEarthState: Earth.EarthState? = null
    private var lastArrowCam: Pair<Float, Float>? = null
    private var lastArrowMs = 0L

    override fun start() {
        running = true
        if (_state.value.tracking == EarthTracking.OFF) _state.update { it.copy(tracking = EarthTracking.PAUSED) }
        if (BuildConfig.ARCORE_API_KEY_SET) Log.i(TAG, "start (API key set)")
        else Log.w(TAG, "start: no ARCORE_API_KEY in local.properties; expect ERROR_NOT_AUTHORIZED, outdoor leg falls back to banner + map")
    }

    override fun stop() {
        running = false
        anchor?.detach(); anchor = null
        pendingAnchor = null
        session = null
        _arrows.value = OutdoorArrowFrame()
        _state.value = GeoState()
        Log.i(TAG, "stop")
    }

    override fun checkVps(lat: Double, lng: Double) {
        pendingVps = LatLng(lat, lng)
        session?.let { runVps(it) }
    }

    override fun placeTerrainAnchor(lat: Double, lng: Double): Boolean {
        pendingAnchor = LatLng(lat, lng)
        val s = session ?: return false
        return tryResolveAnchor(s)
    }

    /** Call from ARScene's sessionConfiguration. Enables Geospatial only when it can work; otherwise records why. */
    fun configure(session: Session, config: Config) {
        this.session = session
        val reason = when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
                "location permission not granted"
            !runCatching { session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED) }.getOrDefault(false) ->
                "Geospatial not supported on this device"
            else -> null
        }
        if (reason != null) {
            config.geospatialMode = Config.GeospatialMode.DISABLED
            fail(reason)
            return
        }
        config.geospatialMode = Config.GeospatialMode.ENABLED
        Log.i(TAG, "Geospatial mode ENABLED")
    }

    /** Call from ARScene's onSessionUpdated. Cheap when not running. */
    fun onFrame(session: Session, frame: Frame) {
        this.session = session
        if (!running) return
        if (pendingVps != null) runVps(session)

        val earth = runCatching { session.earth }.getOrNull()
        if (earth == null) {
            if (_state.value.tracking != EarthTracking.UNAVAILABLE) fail("Earth is null (Geospatial mode off)")
            return
        }
        val es = earth.earthState
        if (es != lastEarthState) { lastEarthState = es; Log.i(TAG, "earth state $es") }
        if (es != Earth.EarthState.ENABLED) {
            fail(when (es) {
                Earth.EarthState.ERROR_NOT_AUTHORIZED -> "ARCore API key missing or not authorized"
                Earth.EarthState.ERROR_RESOURCE_EXHAUSTED -> "ARCore API quota exhausted"
                Earth.EarthState.ERROR_APK_VERSION_TOO_OLD -> "Google Play Services for AR too old"
                else -> "earth state $es"
            })
            return
        }

        val now = SystemClock.elapsedRealtime()
        val tracking = earth.trackingState == TrackingState.TRACKING
        if (!tracking) {
            if (_state.value.tracking != EarthTracking.PAUSED) {
                _state.update { it.copy(tracking = EarthTracking.PAUSED, failure = null) }
                Log.i(TAG, "earth PAUSED (localizing)")
            }
            _arrows.value = OutdoorArrowFrame()
            return
        }
        // Publish at about 4 Hz: every frame would recompose the chip 30 times a second.
        if (now - lastPublishMs >= 250 || _state.value.tracking != EarthTracking.TRACKING) {
            lastPublishMs = now
            val p = earth.cameraGeospatialPose
            val was = _state.value.tracking
            _state.update {
                it.copy(
                    tracking = EarthTracking.TRACKING,
                    lat = p.latitude, lng = p.longitude, headingDeg = p.heading,
                    horizontalAccuracyM = p.horizontalAccuracy, yawAccuracyDeg = p.orientationYawAccuracy,
                    failure = null,
                )
            }
            if (was != EarthTracking.TRACKING) Log.i(TAG, "earth TRACKING at %.6f, %.6f acc %.1f m yaw %.1f deg".format(p.latitude, p.longitude, p.horizontalAccuracy, p.orientationYawAccuracy))
        }
        if (pendingAnchor != null && anchor == null && _state.value.anchor != AnchorStatus.RESOLVING) tryResolveAnchor(session)
        updateArrows(frame, now)
    }

    private fun updateArrows(frame: Frame, now: Long) {
        val a = anchor
        val cam = frame.camera
        if (a == null || a.trackingState != TrackingState.TRACKING || cam.trackingState != TrackingState.TRACKING ||
            !OutdoorArrowGate.shouldDrawArrows(_state.value)) {
            if (_arrows.value.arrows.isNotEmpty()) _arrows.value = OutdoorArrowFrame()
            return
        }
        val cp = cam.pose
        val last = lastArrowCam
        // Rebuild when the phone moved 0.3 m or every second (anchor corrections).
        if (last != null && hypot(cp.tx() - last.first, cp.tz() - last.second) < 0.3f && now - lastArrowMs < 1000) return
        lastArrowCam = cp.tx() to cp.tz(); lastArrowMs = now
        val ap = a.pose
        _arrows.value = OutdoorArrowFrame(
            OutdoorChain.chain(cp.tx().toDouble(), cp.tz().toDouble(), ap.tx().toDouble(), ap.tz().toDouble()),
            (cp.ty() - OUTDOOR_GROUND_BELOW_CAMERA_M).toFloat(),
            com.campusmaps.loc.Vec3(ap.tx().toDouble(), ap.ty().toDouble(), ap.tz().toDouble()),
        )
    }

    private fun runVps(session: Session) {
        val at = pendingVps ?: return
        pendingVps = null
        _state.update { it.copy(vps = VpsStatus.CHECKING) }
        try {
            session.checkVpsAvailabilityAsync(at.lat, at.lng) { v ->
                val s = when (v) {
                    VpsAvailability.AVAILABLE -> VpsStatus.AVAILABLE
                    VpsAvailability.UNAVAILABLE -> VpsStatus.UNAVAILABLE
                    VpsAvailability.UNKNOWN -> VpsStatus.UNKNOWN
                    else -> VpsStatus.ERROR
                }
                Log.i(TAG, "checkVpsAvailability(%.6f, %.6f) = $v".format(at.lat, at.lng))
                _state.update { it.copy(vps = s, failure = if (s == VpsStatus.ERROR) "VPS check: $v" else it.failure) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkVpsAvailabilityAsync failed", e)
            _state.update { it.copy(vps = VpsStatus.ERROR, failure = "VPS check failed: ${e.javaClass.simpleName}") }
        }
    }

    private fun tryResolveAnchor(session: Session): Boolean {
        val at = pendingAnchor ?: return false
        val earth = runCatching { session.earth }.getOrNull() ?: return false
        if (earth.trackingState != TrackingState.TRACKING) return false
        anchor?.detach(); anchor = null
        _state.update { it.copy(anchor = AnchorStatus.RESOLVING) }
        return try {
            // 0 m above terrain; identity rotation (the chain only uses the anchor's position).
            earth.resolveAnchorOnTerrainAsync(at.lat, at.lng, 0.0, 0f, 0f, 0f, 1f) { a, st ->
                Log.i(TAG, "terrain anchor at %.6f, %.6f: $st".format(at.lat, at.lng))
                if (st == Anchor.TerrainAnchorState.SUCCESS && a != null) {
                    anchor = a; pendingAnchor = null
                    _state.update { it.copy(anchor = AnchorStatus.READY) }
                } else {
                    pendingAnchor = null
                    _state.update { it.copy(anchor = AnchorStatus.FAILED, failure = "terrain anchor: $st") }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "resolveAnchorOnTerrainAsync failed", e)
            _state.update { it.copy(anchor = AnchorStatus.FAILED, failure = "terrain anchor failed: ${e.javaClass.simpleName}") }
            false
        }
    }

    private fun fail(reason: String) {
        if (_state.value.failure != reason || _state.value.tracking != EarthTracking.UNAVAILABLE) {
            Log.w(TAG, "unavailable: $reason")
            _state.update { it.copy(tracking = EarthTracking.UNAVAILABLE, failure = reason) }
            _arrows.value = OutdoorArrowFrame()
        }
    }

    private companion object { const val TAG = "Geo" }
}
