package com.campusmaps.glasses

// Glasses mode speaks the current step every cycle (about 9 s). During a lift ride that repeated
// "Take the elevator to floor 6" 4 times in a row (qa-emuA Q4). The gate lets a changed step through at
// once and the same step at most once per [repeatMs]. The camera bursts are not gated; Repeat on S3
// speaks through the guidance controller and never passes here. Pure Kotlin, times are passed in.
class SpeechGate(private val repeatMs: Long = DEFAULT_REPEAT_MS) {

    private var lastText: String? = null
    private var lastAtMs = 0L

    // True: speak [text] now (and remember it). False: same text said less than [repeatMs] ago.
    fun shouldSpeak(text: String, nowMs: Long): Boolean {
        if (text == lastText && nowMs - lastAtMs < repeatMs) return false
        lastText = text
        lastAtMs = nowMs
        return true
    }

    fun reset() {
        lastText = null
        lastAtMs = 0L
    }

    companion object {
        const val DEFAULT_REPEAT_MS = 25_000L
    }
}
