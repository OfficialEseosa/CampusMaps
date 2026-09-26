package com.campusmaps.geo

/** Where the map-to-AR hand-off is. */
enum class HandoffPhase {
    /** The map (Home / Explore) is showing. */
    MAP,
    /** The "Almost there. Point your camera ahead" card is up over the map. */
    CARD,
    /** The ~900 ms map-to-AR animation is running. */
    ANIMATING,
    /** S2 is showing. */
    AR,
}

/**
 * The hand-off state machine. Pure: [HandoffController] drives it from location fixes, the UI from button taps and the
 * end of the animation.
 */
class HandoffStateMachine(val trigger: HandoffTrigger = HandoffTrigger()) {
    var phase: HandoffPhase = HandoffPhase.MAP
        private set

    fun newRoute(entrance: LatLng?) { trigger.reset(entrance); phase = HandoffPhase.MAP }

    /** A fix arrived. Returns the new phase. */
    fun onFix(fix: LocationFix, nowMs: Long): HandoffPhase {
        if (trigger.onFix(fix, nowMs) && phase == HandoffPhase.MAP) phase = HandoffPhase.CARD
        return phase
    }

    /** Manual "AR" button, or the card advancing: start the animation now. */
    fun startHandoff(): HandoffPhase {
        if (phase == HandoffPhase.MAP || phase == HandoffPhase.CARD) { trigger.consume(); phase = HandoffPhase.ANIMATING }
        return phase
    }

    fun animationDone(): HandoffPhase { if (phase == HandoffPhase.ANIMATING) phase = HandoffPhase.AR; return phase }

    /** Back from S2 to the map (End route or system back). The trigger stays consumed until a new route or 60 m. */
    fun backToMap(): HandoffPhase { phase = HandoffPhase.MAP; return phase }
}
