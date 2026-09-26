package com.campusmaps.glasses

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.campusmaps.guidance.GlassesLink
import com.campusmaps.guidance.GlassesPhase
import com.campusmaps.platform.Speaker
import com.campusmaps.platform.TtsStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

// The real glasses cycle (docs/06): look (camera on, 3 stills, camera off), recognise (ML Kit OCR on the
// stills, vote against the building's text anchors), speak (TTS, only after the camera is fully off, and
// waited out before the camera starts again). Camera and audio never overlap.
//
// If the glasses are missing or a burst fails: "Not connected" on S3, retry after 2 s, 4 s, 8 s ... 30 s,
// then the cycle resumes by itself. Same GlassesLink contract as SimulatedGlassesLink, including
// [finished] (stop after the arrival sentence, docs/22 bug 6).
class RealGlassesLink(
    private val scope: CoroutineScope,
    private val speaker: Speaker,
    private val source: StillSource,
    // Building code -> its text anchors (loaded from assets/buildings/*.json by GlassesMode).
    private val anchorsByBuilding: Map<String, List<AnchorText>>,
    private val reader: SignReader = SignReader(),
) : GlassesLink {

    // Debug card "Glasses: connected / unplugged": false tears the session down, true lets it reconnect.
    override val connectedFlag = MutableStateFlow(true)

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _phase = MutableStateFlow(GlassesPhase.LOOKING)
    override val phase: StateFlow<GlassesPhase> = _phase.asStateFlow()

    private val _seen = MutableStateFlow<String?>(null)
    override val seen: StateFlow<String?> = _seen.asStateFlow()

    private val _lastStill = MutableStateFlow<ImageBitmap?>(null)
    override val lastStill: StateFlow<ImageBitmap?> = _lastStill.asStateFlow()

    // Node id the last burst voted for (for localization to pick up later; S3 shows [seen]).
    private val _recognisedNode = MutableStateFlow<String?>(null)
    val recognisedNode: StateFlow<String?> = _recognisedNode.asStateFlow()

    private var job: Job? = null
    private var building: String? = null

    override fun start(currentInstruction: () -> String, currentSign: () -> String?, finished: () -> Boolean) {
        job?.cancel()
        _seen.value = null
        _recognisedNode.value = null
        Log.i(TAG, "start: source ${source.name}")
        job = scope.launch {
            var backoff = FIRST_BACKOFF_MS
            var ready = false
            var cycle = 0
            while (isActive) {
                if (finished()) {
                    Log.i(TAG, "CYCLE end: arrived and said so")
                    _phase.value = GlassesPhase.LOOKING
                    break
                }
                if (!connectedFlag.value) {
                    if (ready || _connected.value) { source.disconnect(); ready = false; _connected.value = false; Log.i(TAG, "unplugged (debug)") }
                    _phase.value = GlassesPhase.LOOKING
                    delay(500)
                    continue
                }
                if (!ready) {
                    ready = try { source.connect() } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(TAG, "connect: $e"); false }
                    _connected.value = ready
                    if (!ready) {
                        Log.i(TAG, "RECONNECT: not connected, retry in ${backoff / 1000} s")
                        _phase.value = GlassesPhase.LOOKING
                        delay(backoff)
                        backoff = min(backoff * 2, MAX_BACKOFF_MS)
                        continue
                    }
                    Log.i(TAG, "connected to ${source.name}")
                    backoff = FIRST_BACKOFF_MS
                }

                // Never turn the camera on while the phone is still talking.
                waitForSilence()

                cycle++
                val sign = currentSign()
                val anchors = anchorsFor(sign)
                val hint = anchors.firstOrNull { sign != null && SignVoter.normalize(it.text) == SignVoter.normalize(sign) }
                _phase.value = GlassesPhase.LOOKING
                val t0 = SystemClock.elapsedRealtime()
                Log.i(TAG, "CYCLE $cycle LOOKING: burst of $STILLS (expect ${hint?.anchorId ?: "-"} '${sign ?: ""}')")
                val stills: List<Bitmap> = try {
                    source.burst(STILLS, hint?.anchorId)
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    Log.w(TAG, "RECONNECT: burst failed ($e), retry in ${backoff / 1000} s")
                    source.disconnect()
                    ready = false
                    _connected.value = false
                    delay(backoff)
                    backoff = min(backoff * 2, MAX_BACKOFF_MS)
                    continue
                }
                // The camera is off from here on (burst() returns only after stream and session stopped).
                stills.lastOrNull()?.let { _lastStill.value = it.asImageBitmap() }

                _phase.value = GlassesPhase.RECOGNISING
                val texts = withContext(Dispatchers.Default) {
                    stills.mapIndexed { i, b ->
                        val t = runCatching { reader.read(b) }.getOrElse { Log.w(TAG, "OCR failed: $it"); "" }
                        Log.i(TAG, "OCR still ${i + 1}: '${t.replace('\n', '|')}'")
                        t
                    }
                }
                val vote = SignVoter.vote(texts, anchors, preferNodes = setOfNotNull(hint?.nodeId))
                if (vote != null) {
                    _seen.value = vote.anchor.text
                    _recognisedNode.value = vote.nodeId
                    Log.i(TAG, "VOTE node ${vote.nodeId} (${vote.anchor.anchorId} '${vote.anchor.text}') ${vote.votes}/${vote.stills} conf ${"%.2f".format(vote.confidence)}")
                } else {
                    Log.i(TAG, "VOTE none (${stills.size} stills)")
                }

                _phase.value = GlassesPhase.SPEAKING
                val text = currentInstruction()
                Log.i(TAG, "SPEAK start (camera off, cycle ${SystemClock.elapsedRealtime() - t0} ms so far): '$text'")
                speakAndWait(text)
                Log.i(TAG, "SPEAK done, cycle $cycle took ${SystemClock.elapsedRealtime() - t0} ms")
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        speaker.stop()
        source.disconnect()
        _connected.value = false
        _phase.value = GlassesPhase.LOOKING
    }

    // The building whose anchors contain the expected sign; sticks once known. Unknown: all buildings.
    private fun anchorsFor(sign: String?): List<AnchorText> {
        if (sign != null) {
            val n = SignVoter.normalize(sign)
            anchorsByBuilding.entries.firstOrNull { (_, list) -> list.any { SignVoter.normalize(it.text) == n } }?.let { building = it.key }
        }
        return building?.let { anchorsByBuilding[it] } ?: anchorsByBuilding.values.flatten()
    }

    private suspend fun waitForSilence() {
        var waited = 0
        while (speaker.isSpeaking.value && waited < MAX_SPEECH_MS) { delay(100); waited += 100 }
        if (speaker.isSpeaking.value) speaker.stop()
    }

    private suspend fun speakAndWait(text: String) {
        if (speaker.status.value != TtsStatus.READY) {
            delay(2_000) // No voice: still show the phase
            return
        }
        speaker.speak(text)
        var waited = 0
        while (!speaker.isSpeaking.value && waited < 1_500) { delay(50); waited += 50 }
        while (speaker.isSpeaking.value && waited < MAX_SPEECH_MS) { delay(100); waited += 100 }
        delay(300) // Let the Bluetooth audio tail finish before the camera starts
    }

    companion object {
        const val TAG = "GlassesLink"
        const val STILLS = 3
        const val FIRST_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val MAX_SPEECH_MS = 30_000
    }
}
