package com.campusmaps.geo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * FusedLocation fixes for the 40 m trigger and the outdoor banner distance, once a second.
 * Without the location permission (or Play Services) it logs why and emits nothing; the manual AR button still works.
 */
object FusedLocationFixes {
    private const val TAG = "Geo"

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun flow(context: Context, intervalMs: Long = 1000): Flow<LocationFix> {
        if (!hasPermission(context)) {
            Log.w(TAG, "no location permission: 40 m trigger off, use the AR button")
            return emptyFlow()
        }
        return callbackFlow {
            val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
            val cb = object : LocationCallback() {
                override fun onLocationResult(r: LocationResult) {
                    r.lastLocation?.let { trySend(LocationFix(it.latitude, it.longitude, it.accuracy.toDouble(), it.time)) }
                }
            }
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs).setMinUpdateIntervalMillis(intervalMs / 2).build()
            try {
                client.requestLocationUpdates(req, cb, Looper.getMainLooper())
            } catch (e: Exception) {
                Log.w(TAG, "FusedLocation unavailable", e)
            }
            awaitClose { client.removeLocationUpdates(cb) }
        }
    }
}
