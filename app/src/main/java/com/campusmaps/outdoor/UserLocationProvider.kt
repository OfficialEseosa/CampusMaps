package com.campusmaps.outdoor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

// The student's outdoor position from the fused location provider (GPS + Wi-Fi). Everything on-device.
class UserLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    private val _fix = MutableStateFlow<UserFix?>(null)
    val fix: StateFlow<UserFix?> = _fix.asStateFlow()

    private var updating = false

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { _fix.value = UserFix(it.latitude, it.longitude, it.accuracy) }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (updating || !hasPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L).setMinUpdateIntervalMillis(1_000L).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        updating = true
        client.lastLocation.addOnSuccessListener { l -> if (l != null && _fix.value == null) _fix.value = UserFix(l.latitude, l.longitude, l.accuracy) }
    }

    fun stop() {
        if (!updating) return
        client.removeLocationUpdates(callback)
        updating = false
    }

    // Last known position within [timeoutMs] (used at start to pick the home screen). Null without permission or fix.
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(timeoutMs: Long = 3_000L): UserFix? {
        if (!hasPermission()) return null
        _fix.value?.let { return it }
        return withTimeoutOrNull(timeoutMs) {
            try {
                val l = client.lastLocation.await()
                    ?: client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                l?.let { UserFix(it.latitude, it.longitude, it.accuracy) }
            } catch (e: Exception) {
                null
            }
        }
    }

    // Seam for the Geospatial hand-off (about 40 m from the door): live distance to a point in metres, null without a fix.
    fun distanceTo(target: LatLngPoint): Flow<Double?> = fix.map { f -> f?.let { OutdoorRoutes.distanceM(it.point, target) } }
}
