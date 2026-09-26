package com.campusmaps.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver

// One screen, display only, nothing tappable. Mirrors the phone's current step.
class WatchActivity : ComponentActivity() {

    private var ambient by mutableStateOf(false)

    // Ambient (always on) mode: the arrow becomes a thin white outline to save the AMOLED.
    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            ambient = true
        }

        override fun onExitAmbient() {
            ambient = false
        }

        override fun onUpdateAmbient() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        setContent {
            val step by WatchStepStore.step.collectAsStateWithLifecycle()
            WatchFace(step = step, ambient = ambient)
        }
    }
}
