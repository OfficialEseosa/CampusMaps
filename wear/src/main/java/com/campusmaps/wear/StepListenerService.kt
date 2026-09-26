package com.campusmaps.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.campusmaps.shared.WatchHaptics
import com.campusmaps.shared.WatchProtocol
import com.campusmaps.shared.WatchStep
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The latest step from the phone. The watch face just draws whatever is here.
object WatchStepStore {
    private val _step = MutableStateFlow<WatchStep?>(null)
    val step: StateFlow<WatchStep?> = _step.asStateFlow()

    // Returns true when the step actually changed (so we only buzz once per step).
    fun update(step: WatchStep): Boolean {
        if (_step.value == step) return false
        val typeChanged = _step.value?.type != step.type || _step.value?.label != step.label
        _step.value = step
        return typeChanged
    }

    // Route ended on the phone: back to the idle "Start a route on your phone" face.
    fun clear() {
        _step.value = null
    }
}

// Receives "/campusmaps/step" and "/campusmaps/clear" messages from the phone app (see WatchBridge on the phone).
class StepListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        handleMessage(this, event.path, event.data)
    }

    companion object {
        // Shared by the Data Layer callback and the debug-only injection receiver.
        fun handleMessage(context: Context, path: String, data: ByteArray) {
            if (path == WatchProtocol.CLEAR_PATH) {
                WatchStepStore.clear() // No buzz: the student chose to stop.
                return
            }
            if (path != WatchProtocol.STEP_PATH) return
            val step = WatchProtocol.decode(data) ?: return
            val firstOfRoute = WatchStepStore.step.value == null
            // Distance ticking down ("12 m" -> "11 m") updates the face without buzzing again.
            if (WatchStepStore.update(step)) Haptics.play(context, step)
            // First step of a route: bring the face to the wrist so the student does not have to open the app.
            if (firstOfRoute) runCatching {
                context.startActivity(android.content.Intent(context, WatchActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { android.util.Log.w("StepListener", "cannot open the watch face: $it") }
        }
    }
}

// Plays the haptic pattern for a step (section 12 table).
object Haptics {
    fun play(context: Context, step: WatchStep) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(WatchHaptics.waveformTimings(step.type), -1))
    }
}
