package com.campusmaps.guidance

// Debounces the "Locate me" state (qa-phone N5): on a desk the position confidence can cross the 0.5 threshold several
// times a second, and the Locate me card and "Reading sign..." pill blinked with it. The card now shows only after the
// confidence has stayed low for [showAfterMs], and goes away only after it has stayed good for [hideAfterMs].
class LocateHysteresis(private val showAfterMs: Long = 2_000, private val hideAfterMs: Long = 1_000) {
    var shown: Boolean = false
        private set
    private var changingSince = -1L // When the raw value started to disagree with [shown]; -1 while they agree

    fun update(rawLocating: Boolean, nowMs: Long): Boolean {
        if (rawLocating == shown) {
            changingSince = -1
            return shown
        }
        if (changingSince < 0) changingSince = nowMs
        if (nowMs - changingSince >= if (rawLocating) showAfterMs else hideAfterMs) {
            shown = rawLocating
            changingSince = -1
        }
        return shown
    }

    fun reset() {
        shown = false
        changingSince = -1
    }
}
