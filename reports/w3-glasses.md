# W3 glasses polish

Branch `w3/glasses`, 2026-09-26 morning. Emulator-5556, mock replay (`debug.campusmaps.glasses=replay`), CS "Outside: Decatur St side" to Room 608, Guide me with glasses. The property is back at `sim`.

| # | Item | Status | How verified |
|---|------|--------|--------------|
| 1 | Ghost of the old instruction behind "You have arrived" | Fixed | The Crossfade on the S3 instruction is gone; the text swaps at once. Screenshots every ~1 s across one arrival, then a screen recording cut to 10 frames per second across a second arrival: "Room 608 is ahead" (orange) is followed directly by "You have arrived" (green) with no frame showing both texts (`reports/w3-glasses-arrival.png`). |
| 2 | Same step spoken every cycle ("Take the elevator to floor 6" 4 times) | Fixed | New `SpeechGate`: an unchanged step is spoken at most once per 25 s (`SpeechGate.DEFAULT_REPEAT_MS`, tunable); a changed step is spoken at once. Bursts continue every cycle. Repeat still goes straight to the speaker (it never passes the gate). Logcat: cycle 6 SPEAK "Take the elevator to floor 6", cycles 7, 8, 9 "SPEAK skip: same step said less than 25 s ago", cycle 10 speaks the new step at once. Applied to the simulated link too. JVM: `SpeechGateTest` 4 tests. |
| 3 | "Seen:" keeps a stale sign | Fixed | New `SeenTracker`: after 2 bursts in a row with no match, S3 shows "Seen: nothing new" (muted, not bold). One miss keeps the sign. `recognisedNode` (localization) is unchanged by misses and only moves when another sign is voted. Seen on screen during the replay ("Seen: nothing new" on the lift ride and on the floor 6 bend, back to "LIBRARY SOUTH" after a match). JVM: `SeenTrackerTest` 4 tests. |
| 4 | Where the voice comes out | Fixed | New line under the connection pill: "Speech: glasses" (green) when media audio goes to a Bluetooth device (A2DP, SCO, LE audio), "Speech: phone speaker" otherwise ("headphones" for wired/USB). Uses `AudioManager.getAudioDevicesForAttributes` (media, speech) on API 33+, re-read every 2 s. No routing changes. Emulator shows "Speech: phone speaker". |
| 5 | Keep the session open between bursts | Open (skipped) | Not attempted, out of time. Change is in `MetaStillSource.burst()` / `stopCamera()`: stop only the stream, keep `session`; check that "BURST camera OFF" (stream CLOSED) still precedes "SPEAK start". |

Build: `:app:assembleDebug :app:testDebugUnitTest` green.

## Re-check on the real glasses (S25)

1. During a long step (lift ride), the voice says the step once, then stays quiet until it changes or 25 s pass; bursts (capture LED) keep going. Repeat speaks at once.
2. Arrival: only "You have arrived" is visible, no faint old text.
3. After walking past a sign, "Seen:" changes to "nothing new" after two empty bursts.
4. The "Speech:" line: with the glasses connected as a Bluetooth audio device in Android settings it should say "Speech: glasses"; if it says "phone speaker", pair the glasses for media audio. Note: any Bluetooth headset reads as "glasses".
5. Logcat `adb logcat -s GlassesLink GlassesMeta`: "SPEAK skip" lines are expected now; "BURST camera OFF" still comes before every "SPEAK start".

## Files changed

- `app/src/main/java/com/campusmaps/ui/screens/GlassesScreen.kt` (Crossfade removed, speech line, "nothing new")
- `app/src/main/java/com/campusmaps/glasses/RealGlassesLink.kt` (gate and seen tracker)
- `app/src/main/java/com/campusmaps/guidance/GlassesLink.kt` (gate in the simulated link)
- `app/src/main/java/com/campusmaps/platform/Speaker.kt` (`SpeechOutput.label`)
- New: `glasses/SpeechGate.kt`, `glasses/SeenTracker.kt`, tests `SpeechGateTest.kt`, `SeenTrackerTest.kt`
- `reports/w3-glasses.md`, `reports/w3-glasses-arrival.png`
