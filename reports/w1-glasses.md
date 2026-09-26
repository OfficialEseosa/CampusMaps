# W1 glasses: real GlassesLink on Meta toolkit 0.7.0

Branch `w1/glasses`. Agent report, 2026-09-25 night.

## What was built

- `glasses/RealGlassesLink.kt`: the burst loop from docs/06. Look (camera on, 3 stills with `capturePhoto()`, stream and session stopped), Recognise (ML Kit OCR on each still, vote), Speak (TTS only after the camera is off; the loop waits for speech to end, plus 300 ms, before the camera starts again). Same `GlassesLink` contract as the simulated link: Looking / Recognising / Speaking, `seen` = the voted sign text, `finished()` ends the cycle after the arrival sentence (docs/22 bug 6). The last still goes to `lastStill`, so the S3 thumbnail stops saying "Waiting for the first still" (WHATS-LEFT item 6). The voted node id is also exposed as `recognisedNode` for localization to use later.
- Reconnect loop: if connect fails or a burst throws (session error, stream error, timeout), S3 shows "Not connected" and the link retries after 2, 4, 8, 16, 30, 30 ... seconds, then resumes the cycle.
- `glasses/MetaStillSource.kt`: toolkit 0.7.0 (API copied from the CameraAccess 0.7.0 sample). `Wearables.initialize`, registration and camera-permission check, `createSession(AutoDeviceSelector())`, `addStream(MEDIUM, 24 fps)`, wait for STREAMING, `capturePhoto()` x3, HEIC decoded and turned upright from EXIF, then stream stop and session stop.
- `glasses/SignVoter.kt` (pure Kotlin, 8 JVM tests in `app/src/test/.../glasses/SignVoterTest.kt`): docs/03 section 2 matching against the building's anchor text and aliases (OCR confusion map O/I/S/B/Z, room numbers must be a whole token, fuzzy word match), majority vote across the burst (2 of 3), per-still floor 0.6, mean confidence floor 0.7. When two nodes share a sign (CS has LIBRARY SOUTH on E-LM2 and H3) the node of the sign the route expects wins.
- `glasses/MockReplay.kt`: debug replay through the toolkit's MockDeviceKit (a mock Ray-Ban Meta, registered, worn). Video feed = 6 s of the CS survey walk CS-W05 (H.265, 504x896). Stills = the survey photos of CS signs A02, A04, A05, A06, A07 (angle, far, straight), resized to 1280 px, in `app/src/debug/assets/glasses-replay/CS/` (debug APK only, 2.3 MB). A burst of 3 returns the three shots of the sign the route expects next; if we have no photos of it, the next sign in the playlist.
- `glasses/GlassesSetupActivity.kt`: invisible helper that asks for Bluetooth, runs registration with the Meta AI app, then asks for the glasses camera permission. Opened automatically (at most once a minute) when the real link finds the app not registered.
- `glasses/GlassesMode.kt` + one line in `AppContainer.kt`: picks the link at app start.

## How the mode is picked

```
adb shell setprop debug.campusmaps.glasses replay   # mock glasses replaying CS photos (debug builds)
adb shell setprop debug.campusmaps.glasses real     # real glasses
adb shell setprop debug.campusmaps.glasses sim      # old simulated link
# unset: real if the Meta AI app is installed, otherwise sim
adb shell am force-stop com.campusmaps              # then reopen the app
```

The emulator has no Meta AI app, so with the property unset it stays on the simulated link and `AppFlowTest.glassesModeStartsAndStops` ("Glasses connected") is unaffected. The S25 has the Meta AI app, so it gets the real link by default.

## Verified on the emulator (replay mode)

Run on emulator-5556, `debug.campusmaps.glasses=replay`, CS, Outside Decatur St side to Room 608, "Guide me with glasses". Logcat (tags GlassesLink, GlassesMeta, GlassesReplay), times 23:15 to 23:17:

- Mock Ray-Ban Meta paired and worn; the first session failed with "No eligible device found" (device not listed yet), the reconnect loop retried after 2 s and connected. Reconnect path exercised once, by accident.
- Cycle 2: session STARTED in 223 ms, camera ON (streaming) at 12.4 s, 3 stills (CS-A05 angle, far, straight; the sign the route expected), camera OFF at 21.5 s. OCR: `LIBRARY SOUTH|Library|Entrance`, `LR1STH|Library|Entrance`, `PUTH|LIbrary|Entrance`. VOTE node E-LM2 (CS-A05 LIBRARY SOUTH) 3/3, confidence 1.00 (the aliases carry stills 2 and 3). SPEAK start came after camera OFF: "Take the elevator to floor 6". S3 showed "Seen: LIBRARY SOUTH" and the thumbnail showed the last still.
- Cycle 3: no expected sign, so replay played the next photos (CS-A02, 95 DECATUR ST). Session 1.7 s, camera on 5.5 s, off 10.1 s. OCR `SzCLASSROOM SOUTH|SSnECATIR ST.`, `TH`, `OM SOUTH`: VOTE none (correct: no false node). SPEAK "Room 608 is ahead" after camera OFF, then "CYCLE end: arrived and said so" (no repeats).
- In every cycle "BURST camera OFF" is logged before "SPEAK start", and the next burst waits for speech to finish.
- The 608 photos never came up: the teammate's simulated walker arrived in about 2 minutes, before a third burst. Use the debug Pause to hold the walker if you want to see the 608 vote.
- Emulator is very slow here (load average 20 to 30, other agents building): OCR took 7 to 30 s per still, a cycle 40 to 75 s. On a phone ML Kit takes well under a second.
- **ANR**: the first run showed "CampusMaps isn't responding" during glasses mode. `Wearables.initialize` and the mock setup ran on the main thread (14 s on this loaded emulator). Fixed in the last commit (connect and burst now run on Dispatchers.IO), built, but **not re-verified**: the re-run hit an ANR at app start before S3 (System UI and Google search also ANR'd at the same time; load average 30), so the emulator itself is the problem. Re-run the S3 flow on a quiet emulator or the S25 to confirm.
- JVM: `SignVoterTest` 8/8; `:app:assembleDebug :app:testDebugUnitTest` green.

## Not tested (needs the real glasses)

- Everything in `MetaStillSource` against real hardware: registration round trip through the Meta AI app (the return link is `campusmaps://` on `GlassesSetupActivity`; the sample used its own scheme the same way), session start time, whether `capturePhoto()` gives HEIC or Bitmap, the EXIF rotation, OCR on real glasses stills.
- Whether stopping the session (not only the stream) every burst is needed. It is the safe choice for "camera and audio never overlap", but each burst pays the session start (the sample saw 5 s warm-up on first start). If cycles are too slow, keep the session open and only stop the stream (change `burst()` / `stopCamera()` in `MetaStillSource`).
- TTS over the glasses' A2DP speaker after the stream stops.

## Owner test with the real glasses (S25 Ultra)

1. Glasses on, charged, paired in the Meta AI app. Developer Mode on (Meta AI app, Settings, App Info, tap the version 5 times; then glasses settings, Developer Mode). DWA must still be 0.7 (docs/06).
2. Install the debug APK from this branch. Leave the property unset (or `adb shell setprop debug.campusmaps.glasses real`).
3. Pick CS, a start and Room 608, then "Guide me with glasses". S3 says "Not connected" at first. The phone asks for Nearby devices, then the Meta AI app opens: approve "Unverified app", then the camera permission. Back in CampusMaps the link retries within 30 s.
4. Expect: the capture LED lights for about 7 to 10 s (session start, stream, 3 photos at about 2.2 s each), goes off, then the phone speaks the instruction, then the LED comes back. The S3 thumbnail shows the last photo; "Seen:" shows a sign text when one was read.
5. Logcat for the report: `adb logcat -s GlassesLink GlassesMeta GlassesSetup GlassesMode`. The lines CYCLE, BURST, OCR, VOTE, SPEAK show the order; "BURST camera OFF" must come before "SPEAK start" in every cycle.
6. Take the glasses off or fold them: S3 should go to "Not connected" and come back by itself.

## Files changed

- `app/src/main/java/com/campusmaps/guidance/GlassesLink.kt` (interface gains `connectedFlag`, which MainViewModel's debug toggle already used)
- `app/src/main/java/com/campusmaps/glasses/` (new: RealGlassesLink, MetaStillSource, MockReplay, SignVoter, SignReader, StillSource, GlassesMode, GlassesSetupActivity)
- `app/src/main/java/com/campusmaps/AppContainer.kt` (one line)
- `app/src/main/AndroidManifest.xml` (GlassesSetupActivity with the `campusmaps://` link)
- `gradle/libs.versions.toml`, `app/build.gradle.kts`: ML Kit text recognition 16.0.1 (bundled). It was pinned in docs/14 but missing from the catalog. **Loc agent: if you add ML Kit too, use the same alias `mlkit-text-recognition` so the merge is trivial.**
- `app/src/debug/assets/glasses-replay/CS/` (replay photos and feed)
- `app/src/test/java/com/campusmaps/glasses/SignVoterTest.kt`

## For other agents

- The debug card text in `ui/CampusMapsApp.kt` still says "connected (simulated)" whatever the link is. Not changed (not my file).
- `GlassesLink` did not change shape except `connectedFlag` moving into the interface.
- Localization can read `(app.glasses as? RealGlassesLink)?.recognisedNode`.
