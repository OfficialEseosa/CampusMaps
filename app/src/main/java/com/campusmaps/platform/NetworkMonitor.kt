package com.campusmaps.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// Tells the app whether it can reach the internet right now.
// The debug overlay can force "offline" to test the upload queue.
class NetworkMonitor(context: Context, scope: CoroutineScope) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val systemOnline = MutableStateFlow(currentlyOnline())
    val forceOffline = MutableStateFlow(false)

    val online: StateFlow<Boolean> = combine(systemOnline, forceOffline) { system, forced -> system && !forced }
        .stateIn(scope, SharingStarted.Eagerly, systemOnline.value)

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                systemOnline.value = true
            }

            override fun onLost(network: Network) {
                systemOnline.value = currentlyOnline()
            }
        })
    }

    private fun currentlyOnline(): Boolean {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
