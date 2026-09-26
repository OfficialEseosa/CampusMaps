# QA emulator A (emulator-5554, API 36, 2026-09-25 23:33 to 00:10)

Build: the main APK already installed (lastUpdate 23:31, commit edfe957). No code changed. Screenshots (360 px wide) in `reports/shots-qa-emuA/`.

Setup notes:
- With no GPS fix, the app now opens on the Explore map, and Back from there leaves the app (bug Q1).
- Demo mode hides the debug card. To get S1 as home with the debug card, I set the emulator GPS to the Klaus origin: `adb -s emulator-5554 emu geo fix -84.39628 33.77721`. That fix is still set.
- Because of that fix, outdoor banners on CS show the real distance from Klaus to Classroom South, about 2.8 km ("in 2819 m"). That is expected, not a bug.
- The glasses property is left at `sim`.

## Test plan results

| # | Check | Result | Evidence |
|---|---|---|---|
| 1a | KL Start 1 to Room 1116: S1b card | Pass | One card, "From here", 0:34, 44 m walk (`01-KL-S1b`) |
| 1b | S2 banner, "Then:", minimap, floor badge | Pass | "Turn left at Atrium north, in 9 m" and "Then: Room 1116 is on your right". Minimap with the route and the F1 badge. Emulator says "AR unavailable on this device" (`02-KL-S2`) |
| 1c | Debug Step to arrival | Pass | Walker paused, then Step: step 2/3, then 3/3, then "You have arrived / Room 1116 is on your right" with Done and Back to routes (`03-KL-arrived`) |
| 1d | Done lands on S1 | Pass | "Where to?"; the debug card says screen: destination |
| 1e | Back to routes says "You are already here" | Pass | S1b shows "From: Room 1116" and the card "You are already here" |
| 1f | From Outside: South main first, North second | Pass | South main 0:45, 58 m. North 1:11, "+26 s: 34 m more walking" (`05-KL-outside-cards`) |
| 2a | CS P1 (Decatur St side) to 608 | Pass | Library South entrance (floor 2), elevator, 1:27, "also via: Classroom South main (floor 2)" (`06-CS-P1-608`) |
| 2b | CS P2 (Walters side) to 608 | Pass | Walters main, elevator, 1:45, "also via: Walters side entrance". Then CS main (floor 2) 2:13 and Library South 2:34 (`07-CS-P2-608`) |
| 2c | Avoid stairs flips the cards live | Pass | Off: the stairs cards appear (2:23, "+56 s: 13 m more walking, 4 floors of stairs"; 95 Decatur 2:47, "+1:20 ..."). On: Walters side, elevator, 2:21 |
| 2d | The difference line does not repeat "elevator" (bug 12) | Pass | "+54 s: 64 m more walking, rides 5 floors" |
| 2e | S2 fake walk shows every step, including "Exit toward" (bug 16) | Pass | Continuous walker, polled every 1 to 3 s. Steps and times: see the note under this table. The exit step "Exit toward Elevator lobby, floor 6" was on screen for about 3.5 s. A Step run shows step 5/8 exit_toward with the F6 badge (`08-CS-exit-step`) |
| 2f | Elevator ride, floor badge F2 to F6, arrival | Pass | "Riding to Floor 6". The badge reads F2 up to step 4/8 and F6 from the exit step. Arrival "Room 608 is ahead" on F6 |
| 3a | CSE Sat 21:00: card-only banner | Pass | "Routed for Sat 21:00 (simulated)" and "Heads up: Main entrance is card-only now. Using West entrance instead.", West 2:04 (`13-CSE-2100`) |
| 3b | Sat 14:00: no banner | Pass (other time) | TimePicker not used. The cycle chip's Tue 10:00 showed no banner, Main entrance first (1:22) and West second (2:04). This is the same case: an open daytime hour |
| 3c | Sat 23:30: No route | Pass (other time) | The cycle chip's Sat 23:00 showed "No route to Room 220: every entrance is card-only at Sat 23:00." No crash. Real time Fri 23:54 showed the same message |
| 3d | S3 replay: Looking / Recognising / Speaking cycle | Pass | `GlassesMode: glasses mode replay`, 10 cycles. In every cycle, "BURST camera OFF" comes before "SPEAK start". The first session failed with "No eligible device found"; the reconnect loop retried after 2 s. Stills took about 3 s each; a cycle took 5 to 26 s (quiet emulator, load 2.7) |
| 3e | "Seen:" line | Pass | "Seen: LIBRARY SOUTH" after `VOTE node E-LM2 3/3 conf 1.00`. The other cycles voted none, which is correct |
| 3f | Thumbnail shows a still | Pass | "Waiting for the first still" changes to "Last still from the glasses" (`14-S3-arrived`) |
| 3g | Arrival shows Done, Back to routes, Repeat | Pass | All three are shown and Fake step is hidden. Logcat: "CYCLE end: arrived and said so", with no repeats after it. Done lands on S1 |
| 3h | **No ANR** | **Pass** | Checked after the whole S3 replay run and after the other flows. `dumpsys activity processes | grep -i anr` matched 0 lines. Logcat had 0 lines with "ANR in" or "isn't responding". No dialog appeared. `AndroidRuntime:E` was empty |
| 3i | `sim` mode still works | Pass | `glasses mode sim`: the cycle runs and shows "Seen: CLASSROOM SOUTH". The thumbnail stays "Waiting for the first still" (the simulated link has no stills). Stop goes to S1b |
| 4a | Jump to node / start point | Partly tested | The link shows the current node ("S1 (tap to pick)", "outside: P2 (tap to pick)"). The picker and "jump outside with reroute" were not driven |
| 4b | Low confidence / Drop confidence 4 s | Pass | "Drop confidence" shows the "Locate me" card |
| 4c | Force reroute | Pass (see Q3) | One tap on the outdoor leg changed the reroute count from 0 to 2 |
| 4d | Skip step | Pass | Step 3/8 went to 4/8 |
| 4e | Walk speed x1 / x4 | Pass | "speed: x4" at the next position tick |
| 4f | Sim time: switch, date chip, cycle shortcut | Pass | The switch shows "SAT 2026-09-26 21:00 (change)" (`12-simtime-chips`). Cycling went Sat 23:00, then Tue 10:00, then real time. The TimePicker was not opened |
| 4g | Approve / Reject shortcuts | Pass (no-op) | No shortcuts were pending, so nothing changed. No crash and no feedback (Q8) |
| 4h | Open Explore map | Pass, but see Q1 | Opens the map. Back from it then leaves the app |
| 4i | AR override | Pass | "AR: forced on (tap to change)" on an emulator with no ARCore, and no crash (`16-ar-forced-on`) |
| 4j | Card drag, body scroll, 36 dp link rows | Scroll passes; drag not checked; rows see Q2 | Swiping inside the body scrolls it (`10-debug-scrolled`). The drag test was lost when Back left the app. Link rows report a 48 dp touch area on a 40 dp pitch (Q2) |
| 5 | Watch: one "Route ended: clearing the watch" per route | Pass | S2 Back to routes = 1, S2 Done = 1, S2 End route = 1, S3 Done = 1, S3 Stop = 1. Reset with no route running = 0, which is correct |

Note for 2e, the S2 walk on CS P1 to 608. Each line is the time since S2 opened, then the banner:
- 3.5 s: Walk to Library South entrance
- 12.3 s: Go through
- 20.0 s: Turn right at Hallway at CS main
- 35.0 s: Turn left at T junction
- 48.6 s: Take the elevator
- 69.3 s: Riding to Floor 6
- 72.2 s: **Exit toward Elevator lobby, floor 6** (on screen about 3.5 s)
- 75.7 s: Turn left at Elevator lobby
- 79.1 s: Turn right at corridor bend
- 82.7 s: Room 608 is ahead
- 88.8 s: arrived

## New bugs

| # | Bug | Severity | Repro | Status | File |
|---|---|---|---|---|---|
| Q1 | Back on the Explore map always leaves the app, including when the map was opened from S1 ("Coming from across campus? See the map") or from the debug card during an S2 route. `openExplore()` sets `exploreHome = true`, and the `BackHandler` is off on EXPLORE. After that the map is home and S1's Back also goes to the map. The only way back to S1 is to pick a room. | Medium | S1 (GPS near a building): tap "Coming from across campus? See the map", then Back. You land on the launcher. | Open. Not fixed because it is a design question (should the map become home once opened?). The likely fix: remember that the map was opened from S1, and on Back call `go(DESTINATION)` without setting `exploreHome`. | `ui/MainViewModel.kt` (`openExplore`, `back`), `ui/CampusMapsApp.kt:104` |
| Q2 | Debug link rows have 126 px (48 dp) touch bounds on a 106 px (40 dp) pitch, so neighbouring rows overlap by about 8 dp. The LinkText is 36 dp, but Compose's minimum touch target enlarges it. | Low (debug only) | S2 debug card, dump the UI. "Cycle simulated time" spans [632,758] and "Open Explore map" spans [738,844]. | Open | `ui/screens/DebugOverlay.kt` |
| Q3 | One "Force reroute" on the outdoor leg counts 2 reroutes. After the 8 m push the student is still off the route 3 s later, so it reroutes again. | Low (debug only) | CS Walters side to 608, open S2, tap Force reroute right away. The reroute count goes from 0 to 2. | Open | `guidance/GuidanceController.kt` |
| Q4 | S3 speaks the same "Take the elevator to floor 6" 4 times during the ride, about every 6 s. Every cycle speaks the current step even when it has not changed. | Low | CS P1 to 608, Guide me with glasses (replay). See the GlassesLink SPEAK lines in cycles 4 to 7. | Open (maybe by design) | `glasses/RealGlassesLink.kt` |
| Q5 | S3 "Seen: LIBRARY SOUTH" stays on screen until arrival on floor 6, although every later cycle voted none. The old sign reads as if it were just seen. | Low | Same run. | Open | `glasses/RealGlassesLink.kt` |
| Q6 | The debug card forgets it was folded when the screen changes: it opens again on S1b and S2. Open, it covers the S1 building chips and the S1b route card. On S1b I had to tap the card's right edge (x 950) to open a route. | Low (debug only) | Fold the card on S1, tap Route. | Open | `ui/screens/DebugOverlay.kt` |
| Q7 | The folded debug card says "no route" on S1b while route options are showing. It means there is no active guidance. | Info | KL Outside to 1116, S1b, fold the card. | Open | `ui/CampusMapsApp.kt` |
| Q8 | Approve / Reject pending shortcuts give no feedback when nothing is pending. | Info | Tap either one with no S4 submission. | Open | `ui/CampusMapsApp.kt` |

## Not covered (time-box)
- The TimePicker itself (exact Sat 14:00 and 23:30).
- The jump-to-node picker and "jump outside with reroute".
- Dragging the debug card.
- Glasses on CSE itself: the replay stills are CS only, so the S3 run used CS P1 to 608, the same route as the glasses agent's run.
