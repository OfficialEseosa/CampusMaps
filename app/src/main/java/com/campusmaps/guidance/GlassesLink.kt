package com.campusmaps.guidance

import androidx.compose.ui.graphics.ImageBitmap
import com.campusmaps.platform.Speaker
import com.campusmaps.platform.TtsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// The three phases shown on the S3 cycle indicator. Camera and audio never overlap:
// the glasses look, then read what they saw, then speak, then look again.
enum class GlassesPhase(val label: String) {
    LOOKING("Looking"),
    RECOGNISING("Recognising"),
    SPEAKING("Speaking"),
}

// The seam for the smart glasses. The real glasses SDK implements this later.
interface GlassesLink {
    val connected: StateFlow<Boolean>
    val phase: StateFlow<GlassesPhase>
    val seen: StateFlow<String?>          // Last sign text read, e.g. "ELEVATORS"
    val lastStill: StateFlow<ImageBitmap?> // Last camera still from the glasses

    // [finished] true ends the cycle (after the arrival sentence has been said once); Repeat still speaks.
    fun start(currentInstruction: () -> String, currentSign: () -> String?, finished: () -> Boolean = { false })
    fun stop()
}

// Pretend glasses for demos: always "connected" (debug can unplug them) and cycles
// Looking -> Recognising -> Speaking using the phone's speaker.
class SimulatedGlassesLink(
    private val scope: CoroutineScope,
    private val speaker: Speaker,
) : GlassesLink {

    val connectedFlag = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = connectedFlag.asStateFlow()

    private val _phase = MutableStateFlow(GlassesPhase.LOOKING)
    override val phase: StateFlow<GlassesPhase> = _phase.asStateFlow()

    private val _seen = MutableStateFlow<String?>(null)
    override val seen: StateFlow<String?> = _seen.asStateFlow()

    override val lastStill: StateFlow<ImageBitmap?> = MutableStateFlow(null)

    private var job: Job? = null

    override fun start(currentInstruction: () -> String, currentSign: () -> String?, finished: () -> Boolean) {
        job?.cancel()
        _seen.value = null
        job = scope.launch {
            while (isActive) {
                // Arrived and said so: stop cycling. It re-spoke "Room 220 is on your right" every 5 s until Stop (docs/22 #6).
                if (finished()) {
                    _phase.value = GlassesPhase.LOOKING
                    break
                }
                if (!connectedFlag.value) {
                    _phase.value = GlassesPhase.LOOKING
                    delay(500)
                    continue
                }
                _phase.value = GlassesPhase.LOOKING
                delay(2_500)
                _phase.value = GlassesPhase.RECOGNISING
                delay(1_200)
                currentSign()?.let { _seen.value = it }
                _phase.value = GlassesPhase.SPEAKING
                speakAndWait(currentInstruction())
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        speaker.stop()
    }

    private suspend fun speakAndWait(text: String) {
        if (speaker.status.value != TtsStatus.READY) {
            delay(2_000) // No voice available: still show the phase so the cycle reads on video
            return
        }
        speaker.speak(text)
        // Wait for speech to start, then to finish (at most 8 s in total).
        var waited = 0
        while (!speaker.isSpeaking.value && waited < 1_000) { delay(50); waited += 50 }
        while (speaker.isSpeaking.value && waited < 8_000) { delay(100); waited += 100 }
    }
}
