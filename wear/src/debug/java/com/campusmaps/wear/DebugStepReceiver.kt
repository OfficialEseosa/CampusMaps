package com.campusmaps.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.campusmaps.shared.WatchProtocol
import com.campusmaps.shared.WatchStep
import com.campusmaps.shared.WatchStepType
import com.google.android.gms.wearable.Wearable

// DEBUG BUILDS ONLY. Lets us feed the watch the exact bytes the phone's WatchBridge sends,
// without a paired phone:
//   adb shell am broadcast -n com.campusmaps/com.campusmaps.wear.DebugStepReceiver \
//       --es type LEFT --es big "10 m" --es label "Atrium north" [--ez datalayer true]
// Route ended (back to the idle face):
//   adb shell am broadcast -n com.campusmaps/com.campusmaps.wear.DebugStepReceiver --ez clear true [--ez datalayer true]
// With datalayer=true the payload goes through the Wearable MessageClient to the local node
// (so StepListenerService.onMessageReceived is hit for real); otherwise it is handed straight
// to the same handler the listener uses.
class DebugStepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path: String
        val bytes: ByteArray
        val what: String
        if (intent.getBooleanExtra("clear", false)) {
            path = WatchProtocol.CLEAR_PATH
            bytes = ByteArray(0)
            what = "clear"
        } else {
            val type = intent.getStringExtra("type")?.let { t -> WatchStepType.entries.firstOrNull { it.name == t } }
            if (type == null) {
                Log.w(TAG, "Unknown or missing type extra")
                return
            }
            val step = WatchStep(type, intent.getStringExtra("big").orEmpty(), intent.getStringExtra("label").orEmpty())
            path = WatchProtocol.STEP_PATH
            bytes = WatchProtocol.encode(step)
            what = step.toString()
        }
        if (!intent.getBooleanExtra("datalayer", false)) {
            StepListenerService.handleMessage(context, path, bytes)
            Log.i(TAG, "Injected $what directly")
            return
        }
        val pending = goAsync()
        val app = context.applicationContext
        Wearable.getNodeClient(app).localNode
            .continueWithTask { Wearable.getMessageClient(app).sendMessage(it.result.id, path, bytes) }
            .addOnCompleteListener {
                Log.i(TAG, "Data Layer send of $what: ${if (it.isSuccessful) "ok" else it.exception}")
                pending.finish()
            }
    }

    private companion object {
        const val TAG = "DebugStepReceiver"
    }
}
