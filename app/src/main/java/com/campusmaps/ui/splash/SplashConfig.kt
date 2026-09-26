package com.campusmaps.ui.splash

import androidx.compose.ui.graphics.Color

/**
 * Master switch for the animated Compose splash. Set to false to remove it: the app content then
 * shows right after the (static) system splash. The system splash itself stays because the
 * launch theme needs installSplashScreen() to switch to the app theme.
 */
const val SHOW_SPLASH = true

/**
 * All splash timings, in milliseconds from the first frame. To make the splash shorter or longer,
 * change [SCALE] (1.0 = about 1.4 s). Each phase is a start and an end on the same clock.
 */
object SplashTiming {
    /** Multiplies every time below. 0.7 gives about 1.0 s, 1.3 about 1.8 s. */
    const val SCALE = 1.0f

    // The small logo mark from the system splash fades out while the big frame draws in.
    const val HANDOFF_START = 0f
    const val HANDOFF_END = 220f

    // Viewfinder corners draw in (stroke trim).
    const val CORNERS_START = 80f
    const val CORNERS_END = 560f

    // Pin drops from above with a small overshoot.
    const val PIN_START = 300f
    const val PIN_END = 760f

    // Four chevrons fade in bottom to top, one every CHEVRON_STAGGER ms.
    const val CHEVRON_START = 520f
    const val CHEVRON_STAGGER = 90f
    const val CHEVRON_FADE = 200f

    // Dot and dashed line between the chevrons and the pin.
    const val LINE_START = 880f
    const val LINE_END = 1060f

    // "CampusMaps" word.
    const val WORD_START = 820f
    const val WORD_END = 1120f

    // Whole overlay crossfades into the app underneath. A tap jumps straight here.
    const val FADE_START = 1150f
    const val TOTAL = 1400f

    /**
     * Size of the logo mark drawn on the first frame; must match drawable/splash_logo.xml
     * (40-unit mark at 3x = 120 dp) so the system splash hands off without a jump.
     */
    const val HANDOFF_MARK_DP = 120f
}

/** Splash colours, from the style guide. */
object SplashColors {
    val Ink = Color(0xFF313131)
    val Clay = Color(0xFFC67C4E)
    /** Clay over Ink at this alpha; values/splash_colors.xml holds the pre-mixed result. */
    const val CLAY_OVERLAY_ALPHA = 0.20f
    val Mark = Color.White
}
