# QA emulator B (emulator-5556, API 36, 1080x2400, 420 dpi, 2026-09-25)

Branch `qa/emuB`, built from main `edfe957`. The emulator was rebooted first: its system process showed "isn't responding" after the earlier OCR job. Cold starts on this emulator take 5 to 16 s (15.6 s, 12.8 s, 6.4 s, 5.7 s), which is emulator speed, not the app. The first half of the session ran the main APK. After the Explore Back fix (bug 1 below), the APK built from this branch was installed with `install -r`. `:app:assembleDebug` is green. Glasses and watch were not connected (emulator). Screenshots are in `reports/shots-qa-emuB/`.

Device state was restored afterwards: night mode is off, as it was at the start. Animator scale is 1. Demo mode is off. Location permission is revoked again.

## Bugs

| # | Bug | Severity | Repro | Status | File |
|---|---|---|---|---|---|
| 1 | Opening Explore from S1 made Explore the home screen for good. `openExplore()` set `exploreHome = true`, so when S1 was home (near a building, fix at the CS origin), the S1 row "Coming from across campus? See the map" went to Explore, and Back then **closed the app** instead of going back to S1. | Medium | Location on, fix at 33.75288, -84.38769 (CS origin), demo off. S1 opens as home. Tap "See the map", then Back. | **Fixed on `qa/emuB`.** The new `exploreBackToS1` flag is set when Explore is opened while S1 is home. Back on Explore then returns to S1, and S1 stays home. The Explore search field clears the flag. Verified: row, then Explore, then Back gives S1, and Back again exits (`07e`, `07f`). The Explore-as-home behaviour (no fix) is unchanged: Back on Explore exits. | `ui/MainViewModel.kt`, `ui/CampusMapsApp.kt` |
| 2 | With "Remove animations" on, S1 flashes for one frame before the app switches to Explore. Explore-as-home is decided in a `LaunchedEffect` after the first composition. With animations on, the splash overlay hides this. | Low | Explore-home conditions (no fix, demo off), `animator_duration_scale 0`, cold start. | Open | `ui/CampusMapsApp.kt` (`LaunchedEffect` calling `showExploreAsHome`) |
| 3 | One screencap showed a fully black frame between the system splash and the Compose splash (`01b`, first frame). It happened in 1 of 4 captured launches and only in the run with tap spam. It is probably the emulator's surface swap, not the app. | Info | Cold start with screencap in a loop. | Check on the S25 | - |
| 4 | The S1 row "See the map" (and "Add a shortcut") is hidden in demo mode. So in demo mode, the debug link is the only way to reach Explore from S1, and debug is hidden in demo mode too. The code does this on purpose (`if (!demo)`). Worth a decision for the expo: if the demo should show the map leg, there is no way to reach it in demo mode. | Info | Demo mode on, S1. | Decision needed | `ui/screens/DestinationScreen.kt:146` |
| 5 | Demo C at night: with the demo building set to CSE after about 23:00, Room 220 gives "No route to Room 220: every entrance is card-only at Fri 23:5x". This is correct per the data, but a late-night rehearsal on CSE shows no route. Use the sim-time chip. | Info | CSE, Room 220, real clock after 23:00. | By design | - |

## Test plan

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Splash | **Pass** (tap-to-skip only partly shown) | **Size:** the logo bounding box is 270 px in the system splash and 274 px in the Compose first frame, less than 1 dp apart, so `HANDOFF_MARK_DP = 120` matches and needs no change (`01-splash-frames`). The frames show corners, pin, chevrons, dashed line, "CampusMaps", then a crossfade with the app visible underneath (Explore). **Tap spam:** after the handoff, one frame was near the end of the animation and the next was the app. `input tap` takes about 0.5 s on this emulator, so the skip cannot be timed cleanly. **Animator scale 0:** straight from the system splash to the app with no overlay frames, but see bug 2. `AndroidRuntime` FATAL: 0. |
| 2 | Theme | **Pass** | Night on: S1, S1b, Settings and S4 are dark. S2 and S3 are dark. The Explore map stays light and its status-bar icons are dark and readable. Night off: S1, S1b, Settings, S4 and Explore are light, and S2 and S3 stay dark with white status icons (`02-theme-night-top-day-bottom`). Night mode was restored to "no". |
| 3 | Search | **Pass** | `input text "room%s1"` gave "room 1" and "ROOM608" was kept whole. The Clear X empties the field. "608" then IME Enter selects Room 608 ("Selected", "Route to Room 608"). "zzzq" shows `No rooms match "zzzq"` (`03`). |
| 4 | Settings persistence | **Pass** | TTS off, avoid stairs on, demo on and demo building CSE all survive force-stop (`04`). Reset demo after picking Room 220 and the start "West entrance": the selection is cleared ("Pick a destination"), the start goes back to "Outside: by Main entrance", and the sheet closes. |
| 5 | Shortcut persistence (fix bug 9) | **Not run** (time-box) | Still open for the phone or another agent. |
| 6 | Process death | **Pass** | S2 (CS Room 608): HOME, `am kill`, then relaunch is COLD and lands on a clean S1 (demo). S3 the same: COLD, S1, no crash. Explore (demo off, no fix): COLD, Explore with the "Turn on location" hint. The crash buffer is empty. Note: `am kill` does nothing until the app has been in the background a few seconds (one HOT relaunch). |
| 7 | Explore home rule | **Pass after fix** | No fix, demo off: the location permission prompt shows on the first launches (`07a`). After denying it, Explore opens with "Turn on location to see your route" (`07b`). Demo on: opens on S1. The Explore search field opens S1. Explore stays light in night mode. The S1 row, then Explore, then Back **exited the app** before the fix (bug 1); after the fix it returns to S1. |
| 8 | Memory (PSS) | **Pass** | S1 cold 155 MB, which settles to about 100 MB after GC. S2 is 102 MB (no ARCore on the emulator) and S1b is 99 MB. Four S2 enter/exit cycles gave 100.8, 101.1, 101.3 and 101.3 MB, so there is no leak. |
| 9 | Touch targets | **Pass** | Sizes from `uiautomator dump` at 2.625 px per dp. Explore AR button: 56x56 dp. Show my location: 48x48. Satellite: 48x48. Sheet "Start AR navigation": 371x56. Room chips: 72 to 83 x 48. S1 map row: 371x56. S3 after arrival: Done 371x72, Repeat 179x64, Back to routes 179x64. |
| 10 | New bugs | See the table above | - |

## Logcat

- There is no `AndroidRuntime` FATAL and the crash buffer is empty for the whole session.
- The main-thread noise is emulator dex verification and Maps SDK tile prep (a 177 ms JNI lock on `androidmapsapi-TilePrep_1`).

## Notes for the owner

- Bug 1's fix is two files, and nothing else reads the flag. Please merge `qa/emuB` or cherry-pick the fix commit.
- For bug 4, decide whether demo mode should show the "See the map" row.
