# W1 splash report

Branch `w1/splash`. No device used; checked with `:app:assembleDebug` and three Compose `@Preview`s.

## What was built
1. **System splash** (`androidx.core:core-splashscreen` 1.2.0). The manifest's `Theme.CampusMaps` is now the launch theme: background `#4F4037` (Ink #313131 with Clay #C67C4E at 20 % on top, pre-mixed) and the white logo mark (viewfinder corners around a map pin) as the icon. `installSplashScreen()` in `MainActivity` switches to `Theme.CampusMaps.App` (the old window theme, renamed). No keep-on-screen condition, so it never waits on anything; it ends at the first frame. No manifest change needed.
2. **Compose splash overlay** (`ui/splash/SplashOverlay.kt`), drawn on top of the app, which is composed underneath from the start:
   - first frame: the same logo mark, same size and colour as the system splash, so the handoff has no jump; it fades out and grows slightly (0 to 220 ms),
   - viewfinder corners of the "01 Welcome" board draw in by stroke trim (80 to 560 ms),
   - the pin drops from above with a small overshoot bounce (300 to 760 ms),
   - 4 chevrons fade in one by one, bottom to top (520 ms, every 90 ms),
   - the dot pops and the dashed line runs up to the pin (880 to 1060 ms),
   - "CampusMaps" in Sora Bold fades and slides up (820 to 1120 ms),
   - the whole overlay crossfades into the app (1150 to 1400 ms).
   Tap anywhere to skip (jumps to the crossfade). "Remove animations" (animator scale 0) skips the overlay entirely. Saved across configuration changes so it does not replay.
3. **Launcher icon**: `ic_launcher_foreground.xml` is now the logo mark in white on Clay Deep `#A85F33` (the existing `launcher_background`), inside the 66 dp safe zone; also used as the monochrome icon.

## Cold start
Nothing is added before the first frame: no images, no loading, one `Settings.Global` read. The clock (one `Animatable`) starts after the first frame (`withFrameNanos`). The time is read only in the draw and graphics-layer phases, so the animation does not recompose; everything is Canvas paths.

## How to tune
- Whole duration: `SplashTiming.SCALE` in `ui/splash/SplashConfig.kt` (1.0 = 1.4 s, 0.7 = about 1.0 s).
- Single phases: the `*_START` / `*_END` constants in the same object (ms).
- Turn it off: `SHOW_SPLASH = false` (the static system splash stays; it is needed for the theme switch).
- Tint strength: `SplashColors.CLAY_OVERLAY_ALPHA`; if changed, update the pre-mixed `splash_background` in `res/values/splash_colors.xml`.
- Handoff size: if the emulator shows the first-frame mark a different size from the system icon, change `HANDOFF_MARK_DP` (120) to match.

## To check on the emulator/phone
- Handoff from the system icon to the Compose first frame (size match; `HANDOFF_MARK_DP`).
- On Android 12+ the platform may clip the splash icon to a circle; the mark sits inside the 192 dp safe circle, so it should be untouched.
- `AppFlowTest`: the overlay is tagged `splash` and has no text in the semantics tree; the test clock runs it out on the first idle wait.

## Files
- `app/src/main/java/com/campusmaps/MainActivity.kt` (installSplashScreen, `SplashHost { ... }` around the existing content)
- `app/src/main/java/com/campusmaps/ui/splash/SplashConfig.kt`, `SplashOverlay.kt` (new)
- `app/src/main/res/values/themes.xml`, `values-night/themes.xml`, `values/splash_colors.xml` (new), `drawable/splash_logo.xml` (new), `drawable/ic_launcher_foreground.xml`
- `gradle/libs.versions.toml`, `app/build.gradle.kts` (core-splashscreen), `docs/14-tech-stack.md` (version recorded)
