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

    fun send(step: WatchStep) {
        if (step == lastSent) return // Same step, no need to buzz the watch again
        lastSent = step
        scope.launch {
            try {
                val bytes = WatchProtocol.encode(step)
                for (node in nodeClient.connectedNodes.await()) {
                    messageClient.sendMessage(node.id, WatchProtocol.STEP_PATH, bytes).await()
                }
            } catch (e: Exception) {
                // No Play services or no watch: the phone works fine without it.
                Log.i(TAG, "Watch not reachable: ${e.message}")
            }
        }
    }

    fun reset() {
        lastSent = null
    }

    // The route ended (Done / End route / Stop): tell the watch to go back to its idle face.
    fun clear() {
        lastSent = null
        scope.launch {
            try {
                for (node in nodeClient.connectedNodes.await()) {
                    messageClient.sendMessage(node.id, WatchProtocol.CLEAR_PATH, ByteArray(0)).await()
                }
            } catch (e: Exception) {
                Log.i(TAG, "Watch not reachable: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "WatchBridge"
    }
}
