package com.campusmaps.platform

import android.content.Context
import android.util.Log
import com.campusmaps.shared.WatchProtocol
import com.campusmaps.shared.WatchStep
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Mirrors the current step to the Galaxy Watch app (section 12).
// Sends a tiny message to every connected watch. If no watch is paired, nothing happens.
class WatchBridge(context: Context, private val scope: CoroutineScope) {

    private val nodeClient = Wearable.getNodeClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private var lastSent: WatchStep? = null

    // Connected watch count seen on the last send (-1 = not asked yet). Shown in the debug overlay.
    val connectedCount = kotlinx.coroutines.flow.MutableStateFlow(-1)

    // "Watch not reachable" at most once per 30 s: without a watch every step send failed and logged about once a second.
    private var lastUnreachableLogMs = 0L
    private var unreachableSkipped = 0

    private fun logUnreachable(e: Exception) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastUnreachableLogMs != 0L && now - lastUnreachableLogMs < UNREACHABLE_LOG_MS) { unreachableSkipped++; return }
        val more = if (unreachableSkipped > 0) " ($unreachableSkipped more since the last line)" else ""
        Log.i(TAG, "Watch not reachable: ${e.message}$more")
        lastUnreachableLogMs = now
        unreachableSkipped = 0
    }

    fun send(step: WatchStep) {
        if (step == lastSent) return // Same step, no need to buzz the watch again
        lastSent = step
        scope.launch {
            try {
                val bytes = WatchProtocol.encode(step)
                val nodes = nodeClient.connectedNodes.await()
                connectedCount.value = nodes.size
                for (node in nodes) {
                    messageClient.sendMessage(node.id, WatchProtocol.STEP_PATH, bytes).await()
                }
            } catch (e: Exception) {
                // No Play services or no watch: the phone works fine without it.
                connectedCount.value = 0
                logUnreachable(e)
            }
        }
    }

    fun reset() {
        lastSent = null
    }

    // The route ended (Done / End route / Stop): tell the watch to go back to its idle face.
    fun clear() {
        lastSent = null
        Log.i(TAG, "Route ended: clearing the watch")
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                for (node in nodes) {
                    messageClient.sendMessage(node.id, WatchProtocol.CLEAR_PATH, ByteArray(0)).await()
                }
                Log.i(TAG, "Watch clear sent to ${nodes.size} watch(es)")
            } catch (e: Exception) {
                logUnreachable(e)
            }
        }
    }

    private companion object {
        const val TAG = "WatchBridge"
        const val UNREACHABLE_LOG_MS = 30_000L
    }
}

// Clears the watch exactly once per route: every exit (S2 Done / End route, S3 Stop / Done / Back to routes,
// Reset, Reset demo) goes through MainViewModel.stopSession, and Done calls it twice (done -> reset).
// Pure Kotlin so the rule is unit tested (WatchClearOnceTest).
class WatchClearOnce(private val clear: () -> Unit) {
    private var active = false

    fun routeStarted() {
        active = true
    }

    fun routeEnded() {
        if (!active) return
        active = false
        clear()
    }
}
